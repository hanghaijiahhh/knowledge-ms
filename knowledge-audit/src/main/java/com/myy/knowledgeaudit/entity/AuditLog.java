package com.myy.knowledgeaudit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private Long targetId;
    private String targetName;
    private String ipAddress;
    private String userAgent;
    private String requestParams;
    private Integer responseStatus;
    private Long costTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
