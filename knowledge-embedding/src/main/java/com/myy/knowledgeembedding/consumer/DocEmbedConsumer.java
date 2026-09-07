package com.myy.knowledgeembedding.consumer;

import com.myy.common.constant.RabbitMQConstant;
import com.myy.knowledgeembedding.service.EmbeddingOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消费文档创建/更新事件 → 分块 → 向量化 → 写入 ES
 */
@Slf4j
@Component
public class DocEmbedConsumer {

    private final EmbeddingOrchestrator orchestrator;

    public DocEmbedConsumer(EmbeddingOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConstant.DOC_EMBED_QUEUE)
    public void handleDocEvent(Map<String, Object> payload) {
        log.info("收到嵌入任务: docId={}, title={}", payload.get("docId"), payload.get("title"));
        try {
            orchestrator.process(payload);
        } catch (Exception e) {
            log.error("嵌入处理失败 docId={}: {}", payload.get("docId"), e.getMessage(), e);
        }
    }
}
