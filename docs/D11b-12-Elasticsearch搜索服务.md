# D11b-12 技术学习文档：Elasticsearch 搜索服务 + 热搜统计

## 一、本阶段做了什么

```
knowledge-search :8084
├── SearchController      /search 关键词 + /vector 向量 + /hot 热搜 + /admin/reindex
├── SearchService         关键词搜索 + 向量检索 + 权限过滤 + Redis 热搜
├── EmbeddingClient       query 实时向量化（调 SiliconFlow）
├── DocChunk              向量检索结果实体（映射 knowledge_chunks 索引）
├── DocSearchConsumer     MQ 消费者：文档变更 → ES 索引同步
├── DocDocument           ES 文档实体 (@Document)
├── DocRepository         ES Repository (继承 ElasticsearchRepository)
├── ESSearchConfig        ES 客户端配置 + 连接超时
└── UserContextUtil       从 Gateway Header 取当前用户
```

---

## 二、Elasticsearch 在项目中的角色

### 2.1 为什么需要搜索引擎

MySQL `LIKE '%关键词%'` 做不到：
- 忽略大小写匹配
- 中文分词（"请假流程" 应匹配到 "请假" 和 "流程"）
- 高亮搜索结果中的关键词
- 百万级文档的毫秒级查询
- 语义搜索（Dense Vector kNN）

Elasticsearch 本质上是一个基于 Apache Lucene 的分布式搜索引擎，对文本索引做了极致优化。

### 2.2 ES 在项目中的三个用途

| 用途 | 索引名 | 说明 |
|------|--------|------|
| 全文搜索 | `knowledge_doc` | 文档标题 + 内容的 BM25 搜索 |
| 向量检索 | `knowledge_chunks` | Dense Vector kNN 语义搜索（给 Agent 用） |
| 搜索高亮 | `knowledge_doc` | 返回结果中关键词用 `<em>` 标签标记 |

---

## 三、ES 索引设计

### 3.1 DocDocument 实体

```java
@Data
@Document(indexName = "knowledge_doc")
public class DocDocument {

    @Id
    private Long id;             // 与 MySQL doc_info.id 一致

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String content;

    @Field(type = FieldType.Long)
    private Long creatorId;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createTime;
}
```

### 3.2 核心注解解释

| 注解 | 解释 |
|------|------|
| `@Document(indexName = "knowledge_doc")` | 映射到 ES 索引 `knowledge_doc`，不存在则自动创建 |
| `@Id` | ES 文档 ID，设置为 MySQL 主键——两边用同一个 ID，更新/删除时直接根据 ID 操作 |
| `@Field(type = FieldType.Text, analyzer = "ik_max_word")` | 使用 IK 中文分词器，建索引时用 ik_max_word（最大粒度切分） |
| `@Field(type = FieldType.Keyword)` | Keyword 类型不分词，精确匹配（用于 status 字段，只允许 PUBLISHED/DRAFT） |
| `@Field(format = DateFormat.date_hour_minute_second)` | 日期格式化为 `yyyy-MM-dd'T'HH:mm:ss` |

### 3.3 IK 分词器

```
原文："北京市人民法院"

ik_max_word（细粒度）：    北京 | 北京市 | 京市 | 市 | 人民 | 人民法院 | 法院
ik_smart（粗粒度）：       北京 | 市 | 人民法院
```

**为什么 title 用 ik_max_word 而搜索时用 ik_smart？**
- 建索引用 ik_max_word：尽可能多地切出词，提高召回率（搜索"法院"也能匹配到"人民法院"）
- 搜索时 ES 默认用索引时的 analyzer，也可以在搜索请求中指定 ik_smart 提高精度
- 本项目两者都用默认，兼容性最好

### 3.4 Date Format 踩坑

**注意**：`@Field` 注解只作用于创建索引时。如果索引先于注解创建，旧数据可能格式不匹配。详见「踩坑记录 3.1」。

---

## 四、搜索实现

### 4.1 搜索 DSL 构建

```java
public Map<String, Object> searchWithHighlight(
        String keyword, int pageNum, int pageSize, String userInfo) {

    // 1. 获取用户可见文档 ID 列表（权限过滤）
    Long userId = extractUserId(userInfo);
    List<Long> visibleIds = getVisibleDocIds(userId);

    // fail-safe：无权限时直接返回空，不查询 ES
    if (visibleIds.isEmpty()) {
        return Map.of("records", List.of(), "total", 0L, "pageNum", pageNum);
    }

    // 2. 构建 Bool Query
    NativeQuery query = NativeQuery.builder()
        .withQuery(QueryBuilders.boolQuery()
            // must：必须匹配关键词
            .must(QueryBuilders.multiMatchQuery(keyword, "title^3", "content"))
            // filter：权限过滤（不参与评分，只过滤）
            .filter(QueryBuilders.termsQuery("id", visibleIds))
            // filter：只查已发布的文档
            .filter(QueryBuilders.termQuery("status", "PUBLISHED"))
        )
        .withHighlightQuery(new HighlightQuery(
            new Highlight("title", "content")
                .setPreTag("<em>")
                .setPostTag("</em>")))
        // 分页（ES Page 从 0 开始）
        .withPageable(Pageable.ofSize(pageSize).withPage(pageNum - 1))
        .build();

    // 3. 执行搜索
    SearchHits<DocDocument> hits = docRepository.search(query);
    // ...组装返回结果
}
```

### 4.2 `must` vs `filter` 的区别

| | must | filter |
|---|---|---|
| 参与评分 | ✅ 是（匹配度越高分越高） | ❌ 否（命中即可，不关心排名） |
| 缓存 | ❌ 否（分数需要重算） | ✅ 是（无分数，可缓存） |
| 适用场景 | 用户输入的搜索词 | 权限、状态、时间范围等固定条件 |

**`title^3` 的含义**：title 字段权重为 content 的 3 倍。标题命中比正文命中更相关。

### 4.3 高亮

ES 的 Highlight 功能在返回结果中用 `<em>...</em>` 标记匹配的关键词。前端直接渲染 HTML：

```
原文：根据公司规定，员工每年享有5天年假
搜索"年假"后返回：根据公司规定，员工每年享有5天<em>年假</em>
```

### 4.4 结果组装

```java
SearchHits<DocDocument> hits = docRepository.search(query);
List<Map<String, Object>> records = new ArrayList<>();

for (SearchHit<DocDocument> hit : hits) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", hit.getContent().getId());
    m.put("title", hit.getContent().getTitle());
    m.put("content", hit.getContent().getContent());

    // 高亮内容替代原文
    Map<String, List<String>> hl = hit.getHighlightFields();
    if (hl.containsKey("title") && !hl.get("title").isEmpty()) {
        m.put("highlightTitle", hl.get("title").get(0));
    }
    if (hl.containsKey("content") && !hl.get("content").isEmpty()) {
        m.put("highlightContent", hl.get("content").get(0));
    }
    records.add(m);
}

return Map.of(
    "records", records,
    "total", hits.getTotalHits(),
    "pageNum", pageNum,
    "pageSize", pageSize
);
```

### 4.5 向量语义检索（kNN）

关键词搜索依赖分词后的精确匹配，无法理解"怎么请年假"和"年假申请流程"是同一语义。向量检索用 Dense Vector kNN 解决这个问题。

**数据来源**：embedding 服务把文档「切片 → 向量化」后写入 `knowledge_chunks` 索引（`embedding` 字段为 dense_vector 1024 维）。搜索服务通过 `/search/vector` 端点查询该索引。

**查询流程**（`SearchServiceImpl.vectorSearch`）：

```java
public Map<String, Object> vectorSearch(String keyword, int topK, String userInfo) {
    // 1. 权限过滤：fail-safe，无可见文档直接返回空
    List<Long> visibleIds = getVisibleDocIds(userInfo);
    if (visibleIds.isEmpty()) return 空结果;

    // 2. query 实时向量化（与入库批量向量化分离，查询时同步调用）
    List<Float> queryVector = embeddingClient.embed(keyword);

    // 3. kNN 检索：权限 filter 内嵌在 knn 子句（先过滤可见文档，再做向量近邻）
    NativeQuery query = NativeQuery.builder()
        .withQuery(q -> q.knn(k -> k
            .field("embedding")
            .queryVector(queryVector)
            .k(topK)
            .numCandidates(topK * 10)
            .filter(f -> f.terms(t -> t.field("docId")
                .terms(...visibleIds...)))))
        .build();

    // 4. 按 docId 聚合去重（kNN 结果按相似度降序，同一文档多个 chunk 保留最高分）
    SearchHits<DocChunk> hits = esTemplate.search(query, DocChunk.class, ...);
    ...
}
```

**为什么 filter 内嵌在 knn 里而不是 post-filter？**

| | knn 内嵌 filter | post-filter |
|---|---|---|
| 执行顺序 | 先按权限缩小候选集 → 再做向量近邻 | 先 kNN 取 topK → 再过滤 |
| 问题 | 无（语义正确） | topK 里无权限文档居多时，过滤后结果偏少 |

内嵌 filter 是 ES 8.12+ 的 `knn.filter` 特性，通过 `KnnQuery.Builder.filter()` 实现。

**与关键词检索的对比**：

| | 关键词搜索 (/search) | 向量检索 (/search/vector) |
|---|---|---|
| 原理 | BM25 词频统计 | Dense Vector 余弦相似度 |
| 匹配方式 | 分词后精确匹配 | 语义相似 |
| 适用场景 | 精确词、编号、专有名词 | 自然语言、同义改写 |
| 索引 | knowledge_doc | knowledge_chunks |

**Agent 已切换到向量检索**：`KnowledgeSearchTool` 调 `/search/vector`，让 RAG 链路真正走语义检索而非关键词匹配（并顺带修复了中文 query 的 URL 编码）。

---

## 五、Redis 热搜统计

### 5.1 为什么用 Redis ZSet

ZSet（Sorted Set）是有序集合，每个元素有一个 score。热搜场景天然适合：

```
ZSet key: hot:search:keywords
members:
  "请假流程"  → score: 156  (被搜索次数)
  "Spring Boot" → score: 89
  "薪酬制度"  → score: 45
```

### 5.2 记录搜索

```java
public void recordSearch(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) return;
    String key = keyword.trim().toLowerCase();
    redisTemplate.opsForZSet().incrementScore("hot:search:keywords", key, 1);
}
```

`incrementScore` 是原子操作——多个用户同时搜索同一个关键词，计数不会出错。

### 5.3 获取热搜榜

```java
public List<Map<String, Object>> getHotKeywords(int limit) {
    Set<ZSetOperations.TypedTuple<String>> set =
        redisTemplate.opsForZSet()
            .reverseRangeWithScores("hot:search:keywords", 0, limit - 1);

    List<Map<String, Object>> result = new ArrayList<>();
    for (ZSetOperations.TypedTuple<String> t : set) {
        result.add(Map.of(
            "keyword", t.getValue(),
            "count", t.getScore().intValue()
        ));
    }
    return result;
}
```

`reverseRangeWithScores` 按 score 从高到低取 Top-N。

### 5.4 热搜质量保障

**关键设计**：只在搜索到结果时才记录关键词。

```java
// SearchController
Map<String, Object> result = searchService.searchWithHighlight(keyword, ...);
Object total = result.get("total");
if (total instanceof Number && ((Number) total).longValue() > 0) {
    searchService.recordSearch(keyword);  // 只有搜到了才计入热搜
}
```

**为什么？** 如果用户搜索"asdfqwer"这种无意义关键词，ES 返回 0 条结果，却不计入 ZSet——保证热搜榜上每个词都对应到实际可查的文档。

---

## 六、MQ 消费者：实时同步 ES

### 6.1 为什么用 MQ 同步

文档创建 → ES 索引，如果同步做：
- 用户保存文档后要等 ES 索引完成才能响应
- 搜索服务挂了 → 文档创建也失败

用 MQ 异步：
- 文档保存立即返回，索引异步进行
- 搜索服务挂了不影响文档创建，重启后继续消费积压消息

### 6.2 消费者实现

```java
@Component
public class DocSearchConsumer {

    @RabbitListener(queues = "knowledge.doc.create.queue")
    public void handleDocCreate(Map<String, Object> payload) {
        DocDocument doc = new DocDocument();
        doc.setId(((Number) payload.get("docId")).longValue());
        doc.setTitle((String) payload.get("title"));
        doc.setContent((String) payload.get("content"));
        // ...
        docRepository.save(doc);
    }

    @RabbitListener(queues = "knowledge.doc.update.queue")
    public void handleDocUpdate(Map<String, Object> payload) {
        // ES 的 save 是 upsert：存在则覆盖，不存在则新建
        handleDocCreate(payload);
    }

    @RabbitListener(queues = "knowledge.doc.delete.queue")
    public void handleDocDelete(Map<String, Object> payload) {
        Long docId = ((Number) payload.get("docId")).longValue();
        docRepository.deleteById(docId);
    }
}
```

**注意**：MQ 消息体是 `Map<String, Object>`，从 RabbitMQ 消费时 JSON 反序列化后，数字字段类型可能是 `Integer` 或 `Long`，需要统一用 `((Number) map.get("docId")).longValue()` 安全转换。

---

## 七、权限过滤搜索

### 7.1 数据流

```
搜索请求 (带 JWT token)
    │
    ▼
Gateway → X-User-Info 头注入用户信息
    │
    ▼
SearchController → UserContextUtil 取 userId
    │
    ▼
SearchService → Feign 调 doc 服务 GET /doc/visible-ids
    │        → 返回用户可见文档 ID 列表
    │        → Feign 失败 → fallback 返回空列表（安全优先）
    │
    ▼
ES Query → termsQuery("id", visibleIds) 预过滤
```

### 7.2 Feign 接口

```java
@FeignClient(name = "knowledge-doc", fallback = DocServiceFallback.class)
public interface DocServiceClient {
    @GetMapping("/doc/visible-ids")
    Map<String, Object> getVisibleDocIds(@RequestHeader("X-User-Info") String userInfo);
}
```

Fallback 返回空列表——宁可搜不到也不泄露他人文档。

---

## 八、ES 客户端配置

```java
@Configuration
public class ESSearchConfig {

    @Value("${spring.elasticsearch.uris}")
    private String uris;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return ElasticsearchClients.create(
            ClientConfiguration.builder()
                .connectedTo(uris.replace("http://", ""))
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(30))
                .build()
        );
    }
}
```

**超时配置**：连接超时 5 秒、读写超时 30 秒，防止 ES 不可用时请求线程堆积。

---

## 九、关键面试知识点

1. **ES 和 MySQL 的关系？**
   - ES 不是 MySQL 的替代，是补充。MySQL 是主存储（权威数据），ES 是搜索引擎（用来查的快）
   - 数据写入 MySQL → MQ 异步同步到 ES
   - ES 挂了不影响 MySQL，重启后重建索引即可

2. **ES 的倒排索引原理？**
   - MySQL 是"文档 → 词"（正排索引）：`SELECT * WHERE content LIKE '%请假%'` → 全表扫描
   - ES 是"词 → 文档"（倒排索引）：先查"请假"这个词在哪些文档中出现，直接 O(1) 定位

3. **搜索结果的排序策略？**
   - BM25 算法：综合考虑词频（TF）和逆文档频率（IDF）
   - `title^3` 提升标题命中权重
   - 权限过滤不参与评分

4. **Redis ZSet 为什么适合热搜？**
   - 有序（score 排序，天然 Top-N）
   - 原子递增（`incrementScore`，高并发安全）
   - 内存存储（读写快，数据丢失可接受——热搜不是关键数据）

5. **搜索权限为什么用 filter 而不是 must？**
   - filter 不参与评分，速度更快，结果可缓存
   - 权限是二元的（有或无），不需要"匹配度"
   - 用户看到的排名只看关键词相关性，不因权限高低而变化

6. **向量检索和关键词检索的区别？**
   - 关键词（BM25）：基于词频统计，精确匹配，无法理解同义词/语义改写
   - 向量（kNN）：基于 embedding 余弦相似度，理解语义，但无法精确匹配编号/专有名词
   - 两者互补，生产环境常用 RRF（Reciprocal Rank Fusion）混合检索

7. **query 向量化为什么放在 search 服务而不是复用 embedding 服务？**
   - 入库是批量异步向量化（MQ 消费），查询是实时同步向量化，场景不同
   - 查询路径内聚在 search 服务，避免 search → embedding 的同步 Feign 调用增加故障点与延迟

---

*最后更新：2026-08-12*
