package com.myy.knowledgedoc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_info")
public class DocInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private Long categoryId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private String fileMd5;
    private Integer version;
    private String status;       // DRAFT / PUBLISHED / ARCHIVED
    private Long creatorId;
    private String creatorName;
    @TableLogic
    private Integer isDeleted;
    private String extMetadata;  // JSON, 档3预留
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
