package com.myy.knowledgedoc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_version")
public class DocVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Integer version;
    private String title;
    private String content;
    private String fileUrl;
    private Long fileSize;
    private String changeDesc;
    private Long creatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
