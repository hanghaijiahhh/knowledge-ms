package com.myy.knowledgeaudit.consumer;

import com.myy.common.constant.RabbitMQConstant;
import com.myy.knowledgeaudit.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费文档变更事件 → 写入审计日志
 */
@Slf4j
@Component
public class DocAuditConsumer {

    private final AuditLogService auditLogService;

    public DocAuditConsumer(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConstant.AUDIT_QUEUE)
    public void handleDocEvent(Map<String, Object> payload) {
        log.info("审计记录: docId={}, title={}", payload.get("docId"), payload.get("title"));
        auditLogService.saveFromEvent(payload);
    }
}
