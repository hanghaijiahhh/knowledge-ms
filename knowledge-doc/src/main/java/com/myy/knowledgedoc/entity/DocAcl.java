package com.myy.knowledgedoc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("doc_acl")
public class DocAcl {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long userId;
    private Integer permRead;
    private Integer permWrite;
    private Integer permDownload;
    private Long grantedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
