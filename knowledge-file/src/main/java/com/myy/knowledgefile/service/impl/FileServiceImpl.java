package com.myy.knowledgefile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.common.exception.BizException;
import com.myy.knowledgefile.dto.ChunkInitDTO;
import com.myy.knowledgefile.dto.ChunkInitResultDTO;
import com.myy.knowledgefile.entity.FileInfo;
import com.myy.knowledgefile.mapper.FileInfoMapper;
import com.myy.knowledgefile.service.FileService;
import com.myy.knowledgefile.service.WatermarkService;
import io.minio.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FileServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo>
        implements FileService {

    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final WatermarkService watermarkService;

    @Value("${minio.bucket}")
    private String bucket;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "image/png", "image/jpeg", "image/gif",
            "text/plain", "text/markdown"
    );

    private static final long SMALL_FILE_LIMIT = 10 * 1024 * 1024L;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    public FileServiceImpl(MinioClient minioClient, StringRedisTemplate redisTemplate,
                           WatermarkService watermarkService) {
        this.minioClient = minioClient;
        this.redisTemplate = redisTemplate;
        this.watermarkService = watermarkService;
    }

    // ==================== 小文件上传 ====================

    @Override
    @Transactional
    public FileInfo upload(MultipartFile file, Long userId, Long docId) {
        if (file.getSize() > SMALL_FILE_LIMIT) {
            throw new BizException("文件超过10MB，请使用分片上传");
        }
        validateFileType(file);

        String fileKey = generateFileKey(file.getOriginalFilename());
        String md5 = computeMd5(file);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BizException("文件上传失败: " + e.getMessage());
        }

        FileInfo info = new FileInfo();
        info.setOriginalName(file.getOriginalFilename());
        info.setFileKey(fileKey);
        info.setFileSize(file.getSize());
        info.setFileType(file.getContentType());
        info.setFileMd5(md5);
        info.setUploadStatus("COMPLETED");
        info.setUploaderId(userId);
        info.setDocId(docId);
        save(info);
        return info;
    }

    @Override
    public java.util.List<FileInfo> listByDocId(Long docId) {
        return list(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getDocId, docId)
                .eq(FileInfo::getUploadStatus, "COMPLETED")
                .orderByDesc(FileInfo::getCreateTime));
    }

    // ==================== 分片上传 ====================

    @Override
    public ChunkInitResultDTO initChunkUpload(ChunkInitDTO dto, Long userId) {
        // 1. MD5 秒传检测
        if (dto.getFileMd5() != null && !dto.getFileMd5().isEmpty()) {
            FileInfo existing = getOne(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getFileMd5, dto.getFileMd5())
                    .eq(FileInfo::getFileSize, dto.getFileSize())
                    .eq(FileInfo::getUploadStatus, "COMPLETED"));
            if (existing != null) {
                return new ChunkInitResultDTO(null, null, true, existing.getFileKey());
            }
        }

        // 2. 生成上传会话ID和文件Key
        String uploadId = UUID.randomUUID().toString().substring(0, 16);
        String fileKey = generateFileKey(dto.getFileName());

        // 3. Redis 记录上传元数据
        String metaKey = "chunk:upload:" + uploadId;
        Map<String, String> meta = new HashMap<>();
        meta.put("fileKey", fileKey);
        meta.put("fileName", dto.getFileName());
        meta.put("fileSize", String.valueOf(dto.getFileSize()));
        meta.put("fileMd5", dto.getFileMd5() != null ? dto.getFileMd5() : "");
        meta.put("totalChunks", String.valueOf(dto.getTotalChunks()));
        meta.put("uploadedCount", "0");
        meta.put("userId", String.valueOf(userId));
        redisTemplate.opsForHash().putAll(metaKey, meta);
        redisTemplate.expire(metaKey, 24, java.util.concurrent.TimeUnit.HOURS);

        // 4. 创建 FileInfo 记录（UPLOADING 状态）
        FileInfo info = new FileInfo();
        info.setOriginalName(dto.getFileName());
        info.setFileKey(fileKey);
        info.setFileSize(dto.getFileSize());
        info.setFileMd5(dto.getFileMd5());
        info.setUploadStatus("UPLOADING");
        info.setUploaderId(userId);
        save(info);

        return new ChunkInitResultDTO(uploadId, fileKey, false, null);
    }

    @Override
    public void uploadChunk(String uploadId, Integer chunkIndex,
                            MultipartFile chunk, Long userId) {
        String metaKey = "chunk:upload:" + uploadId;
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        if (meta.isEmpty()) throw new BizException("上传会话不存在或已过期");

        // 分片存为临时对象: tmp/{uploadId}/{chunkIndex}
        String chunkKey = "tmp/" + uploadId + "/" + chunkIndex;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(chunkKey)
                    .stream(chunk.getInputStream(), chunk.getSize(), -1)
                    .build());
        } catch (Exception e) {
            throw new BizException("分片 " + chunkIndex + " 上传失败: " + e.getMessage());
        }

        // 标记分片已上传
        redisTemplate.opsForHash().put("chunk:parts:" + uploadId,
                String.valueOf(chunkIndex), "1");
        redisTemplate.opsForHash().increment(metaKey, "uploadedCount", 1);
    }

    @Override
    @Transactional
    public FileInfo mergeChunks(String uploadId, Long userId) {
        String metaKey = "chunk:upload:" + uploadId;
        String partsKey = "chunk:parts:" + uploadId;
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        if (meta.isEmpty()) throw new BizException("上传会话不存在或已过期");

        String fileKey = meta.get("fileKey").toString();
        int totalChunks = Integer.parseInt(meta.get("totalChunks").toString());

        Map<Object, Object> uploadedParts = redisTemplate.opsForHash().entries(partsKey);
        if (uploadedParts.size() < totalChunks) {
            throw new BizException("分片未全部上传 (" + uploadedParts.size() + "/" + totalChunks + ")");
        }

        // composeObject 合并所有分片 → 最终文件
        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            sources.add(ComposeSource.builder()
                    .bucket(bucket)
                    .object("tmp/" + uploadId + "/" + i)
                    .build());
        }

        try {
            // 分批合并：compose 每次最多合并一部分
            mergeSourcesInBatches(sources, fileKey, 0);
        } catch (Exception e) {
            throw new BizException("合并分片失败: " + e.getMessage());
        }

        // 清理临时分片
        for (int i = 0; i < totalChunks; i++) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object("tmp/" + uploadId + "/" + i)
                        .build());
            } catch (Exception ignored) {}
        }

        // 更新 FileInfo 状态
        FileInfo info = lambdaQuery().eq(FileInfo::getFileKey, fileKey).one();
        if (info != null) {
            info.setUploadStatus("COMPLETED");
            updateById(info);
        }

        // 清理 Redis
        redisTemplate.delete(metaKey);
        redisTemplate.delete(partsKey);

        return info;
    }

    /**
     * 递归分批合并，每批最多 1000 个源对象
     */
    private void mergeSourcesInBatches(List<ComposeSource> sources,
                                        String targetKey, int depth) throws Exception {
        if (sources.size() <= 1000) {
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket)
                    .object(targetKey)
                    .sources(sources)
                    .build());
            return;
        }

        // 超过 1000 个分片时，先合并成中间文件再合并
        List<ComposeSource> merged = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += 1000) {
            int end = Math.min(i + 1000, sources.size());
            String batchKey = targetKey + ".batch." + depth + "." + (i / 1000);
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket)
                    .object(batchKey)
                    .sources(sources.subList(i, end))
                    .build());
            merged.add(ComposeSource.builder().bucket(bucket).object(batchKey).build());
        }
        mergeSourcesInBatches(merged, targetKey, depth + 1);

        // 清理中间文件
        for (ComposeSource s : merged) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket).object(s.object()).build());
            } catch (Exception ignored) {}
        }
    }

    // ==================== 下载 ====================

    @Override
    public void download(Long fileId, HttpServletResponse response) {
        FileInfo info = getById(fileId);
        if (info == null) throw new BizException("文件不存在");

        try {
            byte[] data = readFromMinIO(info.getFileKey());
            // 对图片和 PDF 加水印
            String watermarkText = "下载文件";
            data = watermarkService.applyWatermark(data, info.getFileType(), watermarkText);

            response.setContentType(info.getFileType() != null ? info.getFileType()
                    : "application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodeFilename(info.getOriginalName()) + "\"");
            response.getOutputStream().write(data);
            response.flushBuffer();
        } catch (Exception e) {
            throw new BizException("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void preview(Long fileId, String username, HttpServletResponse response) {
        FileInfo info = getById(fileId);
        if (info == null) throw new BizException("文件不存在");

        try {
            byte[] data = readFromMinIO(info.getFileKey());
            String watermarkText = WatermarkService.buildWatermarkText(username);
            data = watermarkService.applyWatermark(data, info.getFileType(), watermarkText);

            response.setContentType(info.getFileType() != null ? info.getFileType()
                    : "application/octet-stream");
            // inline 让浏览器尝试直接渲染（PDF/图片），而不是下载
            response.setHeader("Content-Disposition",
                    "inline; filename=\"" + encodeFilename(info.getOriginalName()) + "\"");
            response.getOutputStream().write(data);
            response.flushBuffer();
        } catch (Exception e) {
            throw new BizException("文件预览失败: " + e.getMessage());
        }
    }

    /** 从 MinIO 读取文件全部字节 */
    private byte[] readFromMinIO(String fileKey) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(fileKey).build())) {
            return stream.readAllBytes();
        }
    }

    // ==================== 工具方法 ====================

    private String generateFileKey(String originalName) {
        String datePath = LocalDate.now().format(DATE_FMT);
        return datePath + "/" + UUID.randomUUID().toString().substring(0, 8) + "/" + originalName;
    }

    private void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BizException("不支持的文件类型: " + contentType);
        }
    }

    private String computeMd5(MultipartFile file) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            try (InputStream is = file.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public IPage<FileInfo> pageQuery(Integer pageNum, Integer pageSize, String keyword) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getUploadStatus, "COMPLETED");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(FileInfo::getOriginalName, keyword);
        }
        wrapper.orderByDesc(FileInfo::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    private String encodeFilename(String name) {
        try {
            return java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return "file";
        }
    }
}
