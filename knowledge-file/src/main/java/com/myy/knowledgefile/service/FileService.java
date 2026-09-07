package com.myy.knowledgefile.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.myy.knowledgefile.dto.ChunkInitDTO;
import com.myy.knowledgefile.dto.ChunkInitResultDTO;
import com.myy.knowledgefile.entity.FileInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileService extends IService<FileInfo> {

    /** 小文件直接上传（<10MB） */
    FileInfo upload(MultipartFile file, Long userId, Long docId);

    /** 查询文档关联的所有文件 */
    java.util.List<FileInfo> listByDocId(Long docId);

    /** 初始化分片上传，返回 uploadId；MD5 重复则秒传跳过 */
    ChunkInitResultDTO initChunkUpload(ChunkInitDTO dto, Long userId);

    /** 上传单个分片 */
    void uploadChunk(String uploadId, Integer chunkIndex,
                     MultipartFile chunk, Long userId);

    /** 合并所有分片 */
    FileInfo mergeChunks(String uploadId, Long userId);

    /** 下载文件（流式输出，带水印） */
    void download(Long fileId, HttpServletResponse response);

    /** 在线预览（流式输出，带水印，Content-Disposition=inline） */
    void preview(Long fileId, String username, HttpServletResponse response);

    /** 分页查询文件列表 */
    IPage<FileInfo> pageQuery(Integer pageNum, Integer pageSize, String keyword);
}
