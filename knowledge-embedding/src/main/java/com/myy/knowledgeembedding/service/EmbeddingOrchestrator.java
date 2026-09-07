package com.myy.knowledgeembedding.service;

import com.myy.knowledgeembedding.entity.DocChunkDocument;
import com.myy.knowledgeembedding.repository.DocChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 嵌入编排：分块 → 向量化 → 写入 ES
 */
@Slf4j
@Service
public class EmbeddingOrchestrator {

    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final DocChunkRepository chunkRepository;

    public EmbeddingOrchestrator(ChunkService chunkService,
                                  EmbeddingService embeddingService,
                                  DocChunkRepository chunkRepository) {
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
    }

    public void process(Map<String, Object> payload) {
        Long docId = toLong(payload.get("docId"));
        String title = toStr(payload.get("title"));
        String content = toStr(payload.get("content"));

        // 删除旧 chunks
        chunkRepository.deleteByDocId(docId);

        // 分块
        List<String> chunks = chunkService.split(title + "\n" + content);
        if (chunks.isEmpty()) {
            log.warn("文档 {} 分块后为空", docId);
            return;
        }

        // 逐块向量化
        List<DocChunkDocument> chunkDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] vector;
            try {
                vector = embeddingService.embed(chunkText);
            } catch (Exception e) {
                log.error("chunk {}/{} 向量化失败，跳过: {}", i + 1, chunks.size(), e.getMessage());
                continue;
            }

            DocChunkDocument chunkDoc = new DocChunkDocument();
            chunkDoc.setChunkId(docId + "_" + i);
            chunkDoc.setDocId(docId);
            chunkDoc.setTitle(title);
            chunkDoc.setChunkIndex(i);
            chunkDoc.setChunkText(chunkText);
            chunkDoc.setEmbedding(vector);
            chunkDoc.setCategoryId(toLong(payload.get("categoryId")));

            chunkDocs.add(chunkDoc);
        }

        if (chunkDocs.isEmpty()) {
            log.warn("文档 {} 所有 chunk 向量化均失败，跳过索引", docId);
            return;
        }

        // 批量写入 ES
        chunkRepository.saveAll(chunkDocs);
        log.info("文档 {} 嵌入完成, chunks={}/{}", docId, chunkDocs.size(), chunks.size());
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
