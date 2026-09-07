package com.myy.knowledgesearch.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.myy.knowledgesearch.client.EmbeddingClient;
import com.myy.knowledgesearch.entity.DocChunk;
import com.myy.knowledgesearch.entity.DocDocument;
import com.myy.knowledgesearch.feign.DocServiceClient;
import com.myy.knowledgesearch.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchTemplate esTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DocServiceClient docServiceClient;
    private final EmbeddingClient embeddingClient;

    private static final String INDEX_NAME = "knowledge_doc";
    private static final String CHUNK_INDEX_NAME = "knowledge_chunks";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public SearchServiceImpl(ElasticsearchTemplate esTemplate, StringRedisTemplate redisTemplate,
                              DocServiceClient docServiceClient, EmbeddingClient embeddingClient) {
        this.esTemplate = esTemplate;
        this.redisTemplate = redisTemplate;
        this.docServiceClient = docServiceClient;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public Map<String, Object> searchWithHighlight(String keyword, int pageNum, int pageSize,
                                                    String userInfo) {
        // 获取用户可见文档ID
        List<Long> visibleIds = getVisibleDocIds(userInfo);

        // 用户无任何可见文档，直接返回空（安全：防止 filter 被跳过导致数据泄露）
        if (visibleIds.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", 0L);
            result.put("items", Collections.emptyList());
            return result;
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.must(m -> m.multiMatch(MultiMatchQuery.of(mm -> mm
                                    .fields("title^3", "content")
                                    .query(keyword)
                                    .type(TextQueryType.BestFields))));
                            // 权限过滤：只返回用户可见的文档
                            b.filter(f -> f.terms(t -> t
                                    .field("docId")
                                    .terms(terms -> terms.value(
                                            visibleIds.stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))));
                            return b;
                        }))
                .withHighlightQuery(buildHighlight())
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<DocDocument> hits = esTemplate.search(query, DocDocument.class,
                IndexCoordinates.of(INDEX_NAME));

        List<Map<String, Object>> items = new ArrayList<>();
        for (SearchHit<DocDocument> hit : hits) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", hit.getContent().getDocId());
            item.put("title", hit.getContent().getTitle());
            item.put("creatorName", hit.getContent().getCreatorName());
            item.put("createTime", hit.getContent().getCreateTime());

            Map<String, List<String>> hlMap = hit.getHighlightFields();
            if (hlMap.containsKey("title")) {
                item.put("titleHighlight", hlMap.get("title").get(0));
            }
            if (hlMap.containsKey("content")) {
                item.put("contentHighlight",
                        String.join("...", hlMap.get("content")));
            }
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", hits.getTotalHits());
        result.put("items", items);
        return result;
    }

    @Override
    public org.springframework.data.domain.Page<DocDocument> search(
            String keyword, int pageNum, int pageSize, String userInfo) {
        List<Long> visibleIds = getVisibleDocIds(userInfo);

        if (visibleIds.isEmpty()) {
            return new org.springframework.data.domain.PageImpl<>(
                    Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.must(m -> m.multiMatch(MultiMatchQuery.of(mm -> mm
                                    .fields("title^3", "content")
                                    .query(keyword))));
                            b.filter(f -> f.terms(t -> t
                                    .field("docId")
                                    .terms(terms -> terms.value(
                                            visibleIds.stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))));
                            return b;
                        }))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<DocDocument> hits = esTemplate.search(query, DocDocument.class,
                IndexCoordinates.of(INDEX_NAME));

        return new org.springframework.data.domain.PageImpl<>(
                hits.getSearchHits().stream()
                        .map(SearchHit::getContent).collect(Collectors.toList()),
                PageRequest.of(pageNum - 1, pageSize),
                hits.getTotalHits());
    }

    @Override
    public Map<String, Object> vectorSearch(String keyword, int topK, String userInfo) {
        // 权限过滤：fail-safe，无可见文档直接返回空
        List<Long> visibleIds = getVisibleDocIds(userInfo);
        if (visibleIds.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", 0L);
            result.put("items", Collections.emptyList());
            return result;
        }

        // 查询侧向量化
        List<Float> queryVector = embeddingClient.embed(keyword);

        // kNN 检索：内嵌权限 filter（先过滤可见文档，再做向量近邻）
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(topK)
                        .numCandidates(topK * 10)
                        .filter(f -> f.terms(t -> t
                                .field("docId")
                                .terms(terms -> terms.value(
                                        visibleIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList())))))))
                .build();

        SearchHits<DocChunk> hits = esTemplate.search(query, DocChunk.class,
                IndexCoordinates.of(CHUNK_INDEX_NAME));

        // 按 docId 聚合：kNN 结果按相似度降序，同一文档保留最高分 chunk
        List<Map<String, Object>> items = new ArrayList<>();
        Set<Long> seenDocIds = new HashSet<>();
        for (SearchHit<DocChunk> hit : hits) {
            Long docId = hit.getContent().getDocId();
            if (docId == null || !seenDocIds.add(docId)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("docId", docId);
            item.put("title", hit.getContent().getTitle());
            item.put("snippet", hit.getContent().getChunkText());
            item.put("score", hit.getScore());
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", (long) items.size());
        result.put("items", items);
        return result;
    }

    @Override
    public List<String> suggest(String prefix) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .matchPhrasePrefix(mpp -> mpp
                                .field("title")
                                .query(prefix)))
                .withMaxResults(5)
                .build();

        SearchHits<DocDocument> hits = esTemplate.search(query, DocDocument.class,
                IndexCoordinates.of(INDEX_NAME));
        return hits.getSearchHits().stream()
                .map(h -> h.getContent().getTitle())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public void recordSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        String today = LocalDate.now().format(DATE_FMT);
        String key = "search:hot:" + today;
        redisTemplate.opsForZSet().incrementScore(key, keyword.trim(), 1);
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }

    private List<Long> getVisibleDocIds(String userInfo) {
        try {
            var resp = docServiceClient.getVisibleDocIds(userInfo);
            if (resp != null && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("获取用户可见文档列表失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private HighlightQuery buildHighlight() {
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .build();

        HighlightField titleField = new HighlightField("title");
        HighlightField contentField = new HighlightField("content");

        return new HighlightQuery(
                new Highlight(params, List.of(titleField, contentField)),
                DocDocument.class);
    }
}
