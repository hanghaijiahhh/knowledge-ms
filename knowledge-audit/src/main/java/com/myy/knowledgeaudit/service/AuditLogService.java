package com.myy.knowledgeaudit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.myy.knowledgeaudit.entity.AuditLog;

import java.util.Map;

public interface AuditLogService extends IService<AuditLog> {

    /** 从 MQ 消息写入审计日志 */
    void saveFromEvent(Map<String, Object> payload);

    /** 分页查询审计日志 */
    IPage<AuditLog> pageQuery(int pageNum, int pageSize,
                              String module, String action, Long userId);
}
