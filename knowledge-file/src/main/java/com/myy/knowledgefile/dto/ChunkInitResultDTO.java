package com.myy.knowledgefile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChunkInitResultDTO {
    private String uploadId;     // MinIO multipart uploadId
    private String fileKey;      // MinIO 对象路径
    private boolean skipUpload;  // true=秒传，跳过上传
    private String existingUrl;  // 秒传时返回已有文件URL
}
