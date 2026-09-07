package com.myy.knowledgesearch.consumer;

import com.myy.common.constant.RabbitMQConstant;
import com.myy.knowledgesearch.entity.DocDocument;
import com.myy.knowledgesearch.repository.DocSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消费文档变更事件 → 同步 ES 索引
 */
@Slf4j
@Component
public class DocEventConsumer {

    private final DocSearchRepository docSearchRepository;

    public DocEventConsumer(DocSearchRepository docSearchRepository) {
        this.docSearchRepository = docSearchRepository;
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConstant.DOC_CREATE_QUEUE)
    public void handleDocCreate(Map<String, Object> payload) {
        log.info("索引新文档: {}", payload.get("docId"));
        DocDocument doc = toDocument(payload);
        docSearchRepository.save(doc);
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConstant.DOC_UPDATE_QUEUE)
    public void handleDocUpdate(Map<String, Object> payload) {
        log.info("更新文档索引: {}", payload.get("docId"));
        DocDocument doc = toDocument(payload);
        docSearchRepository.save(doc);
    }

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConstant.DOC_DELETE_QUEUE)
    public void handleDocDelete(Map<String, Object> payload) {
        Object docId = payload.get("docId");
        log.info("删除文档索引: {}", docId);
        if (docId != null) {
            docSearchRepository.deleteById(Long.valueOf(docId.toString()));
        }
    }

    private DocDocument toDocument(Map<String, Object> payload) {
        DocDocument doc = new DocDocument();
        doc.setDocId(toLong(payload.get("docId")));
        doc.setTitle(toStr(payload.get("title")));
        doc.setContent(toStr(payload.get("content")));
        doc.setCategoryId(toLong(payload.get("categoryId")));
        doc.setStatus(toStr(payload.get("status")));
        doc.setCreatorId(toLong(payload.get("userId")));
        doc.setCreatorName(toStr(payload.get("username")));
        doc.setCreateTime(LocalDateTime.now());
        return doc;
    }

    private String toStr(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.valueOf(obj.toString()); } catch (Exception e) { return 0L; }
    }
}
