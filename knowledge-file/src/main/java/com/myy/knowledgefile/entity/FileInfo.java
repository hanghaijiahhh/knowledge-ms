package com.myy.knowledgefile.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_info")
public class FileInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalName;
    private String fileKey;
    private Long fileSize;
    private String fileType;
    private String fileMd5;
    private String uploadStatus;   // UPLOADING / COMPLETED / FAILED
    private Long docId;            // 关联的文档 ID
    private Long uploaderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
