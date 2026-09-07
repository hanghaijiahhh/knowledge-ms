package com.myy.knowledgefile.controller;

import com.myy.common.dto.ResponseResult;
import com.myy.common.config.UserContextUtil;
import com.myy.knowledgefile.dto.ChunkInitDTO;
import com.myy.knowledgefile.dto.ChunkInitResultDTO;
import com.myy.knowledgefile.dto.ChunkMergeDTO;
import com.myy.knowledgefile.entity.FileInfo;
import com.myy.knowledgefile.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;
    private final UserContextUtil userContextUtil;

    public FileController(FileService fileService, UserContextUtil userContextUtil) {
        this.fileService = fileService;
        this.userContextUtil = userContextUtil;
    }

    /** 小文件上传（<10MB），可关联文档 */
    @PostMapping("/upload")
    public ResponseResult<FileInfo> upload(@RequestParam MultipartFile file,
                                           @RequestParam(required = false) Long docId) {
        Long userId = userContextUtil.getCurrentUserId();
        return ResponseResult.success(fileService.upload(file, userId, docId));
    }

    /** 查询文档关联的所有文件 */
    @GetMapping("/by-doc/{docId}")
    public ResponseResult<java.util.List<FileInfo>> listByDoc(@PathVariable Long docId) {
        return ResponseResult.success(fileService.listByDocId(docId));
    }

    /** 初始化分片上传；返回秒传标志或 uploadId */
    @PostMapping("/chunk/init")
    public ResponseResult<ChunkInitResultDTO> initChunk(@RequestBody ChunkInitDTO dto) {
        Long userId = userContextUtil.getCurrentUserId();
        return ResponseResult.success(fileService.initChunkUpload(dto, userId));
    }

    /** 上传单个分片 */
    @PostMapping("/chunk/upload")
    public ResponseResult<?> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam Integer chunkIndex,
            @RequestParam MultipartFile chunk) {
        Long userId = userContextUtil.getCurrentUserId();
        fileService.uploadChunk(uploadId, chunkIndex, chunk, userId);
        return ResponseResult.success("分片上传成功");
    }

    /** 合并分片 */
    @PostMapping("/chunk/merge")
    public ResponseResult<FileInfo> mergeChunks(@RequestBody ChunkMergeDTO dto) {
        Long userId = userContextUtil.getCurrentUserId();
        return ResponseResult.success(fileService.mergeChunks(dto.getUploadId(), userId));
    }

    /** 文件下载（带水印） */
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        fileService.download(id, response);
    }

    /** 在线预览（带用户水印，inline 方式渲染） */
    @GetMapping("/preview/{id}")
    public void preview(@PathVariable Long id, HttpServletResponse response) {
        String username = userContextUtil.getCurrentUsername();
        fileService.preview(id, username, response);
    }

    /** 查询文件信息（供 doc 服务 Feign 调用） */
    @GetMapping("/info/{id}")
    public ResponseResult<FileInfo> getInfo(@PathVariable Long id) {
        return ResponseResult.success(fileService.getById(id));
    }

    /** 分页查询文件列表 */
    @GetMapping("/page")
    public ResponseResult<com.baomidou.mybatisplus.core.metadata.IPage<FileInfo>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return ResponseResult.success(fileService.pageQuery(pageNum, pageSize, keyword));
    }
}
