# D14-15 技术学习文档：RAG 智能问答 Agent

## 一、本阶段做了什么

```
knowledge-agent :8087 (新增)          knowledge-embedding :8086 (新增)
├── LLMClient          LLM 客户端     ├── DocEmbedConsumer     MQ 消费者
├── AgentOrchestrator  Agent 主循环    ├── EmbeddingOrchestrator  嵌入编排
├── ToolRegistry       工具注册表      ├── ChunkService        文本分块
├── KnowledgeSearchTool 知识检索工具    ├── EmbeddingService    向量化 API
├── ListTreeTool        目录查询工具    └── DocChunkDocument    ES 分块实体
└── SessionMemoryService Redis 记忆
```

---

## 二、什么是 RAG Agent

### 2.1 RAG（Retrieval-Augmented Generation）

传统 LLM 的知识停留在训练数据截止日期，无法回答企业内部的最新文档内容。RAG 的思路是：

```
用户提问 → 检索相关文档 → 把文档片段塞进 Prompt → LLM 基于片段回答
```

这样 LLM 回答就有了"引经据典"的依据，而不是凭空编造。

### 2.2 Agent（Function Calling 模式）

普通的 RAG 是"每次都检索再回答"。Agent 更智能——它让 LLM 自己判断**是否需要**检索、**检索什么**、检索结果够不够：

```
第 1 轮：LLM 判断"这个问题需要查资料"→ 调用 knowledge_search("请假流程")
第 2 轮：LLM 看了结果判断"信息够了" → 生成最终回答（附引用来源）
```

如果 LLM 觉得信息不够，它可以再调用一次工具，换关键词重新搜。最多 3 轮。

### 2.3 和普通聊天机器人的区别

| | 普通聊天 | RAG Agent |
|---|---|---|
| 知识来源 | LLM 训练数据（有截止日期） | 企业内部知识库（实时） |
| 幻觉问题 | 不知道也会编 | 没检索到就坦白说"暂无相关内容" |
| 权限控制 | 无 | 每次检索注入 userId，只看有权限的文档 |
| 可追溯 | 无 | 回答附引用来源 `[来源: xxx]` |

---

## 三、Agent 主循环流程

```
                     ┌──────────┐
                     │ 用户提问  │
                     └────┬─────┘
                          ▼
              ┌──────────────────────┐
              │  LLM 判断是否需要工具  │
              └──────┬───────────────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
   有 tool_calls           有 content
          │                     │
          ▼                     ▼
   ┌─────────────┐      ┌──────────────┐
   │ 执行工具调用  │      │ 返回最终答案   │
   │ + 注入权限   │      └──────────────┘
   └──────┬──────┘
          │
          ▼
   ┌─────────────┐
   │ 反馈结果给LLM│
   └──────┬──────┘
          │
          └──────► 回到"LLM 判断"（最多3轮）
```

核心代码在 `AgentOrchestrator.execute()`：

```java
for (int turn = 0; turn < maxTurns; turn++) {
    LLMResponse llmResp = llmClient.chat(messages, toolRegistry.getDefinitions());
    
    // 情况1: LLM 直接回答（不需要工具）
    if (llmResp.isFinished() && llmResp.getContent() != null) {
        return buildResponse(llmResp.getContent());
    }
    
    // 情况2: LLM 发起 Function Call
    for (ToolCall tc : llmResp.getToolCalls()) {
        String result = toolRegistry.execute(tc.getName(), tc.getParams());
        messages.add(工具调用结果);  // 反馈给下一轮
    }
}
// 情况3: 达到最大轮次，强制生成最终回答
return forceFinalAnswer(messages);
```

---

## 四、为什么需要权限过滤

知识库里有不同部门的文档（人事、财务、技术），不是所有人都有权限看。如果不做权限控制，Agent 可能把薪资文档的内容透露给普通员工。

### 4.1 三层权限保障

1. **网关层**：AuthFilter 校验 JWT，没登录直接 401
2. **工具调用层**：AgentOrchestrator.validateAndSanitize() 向工具参数注入 userId 和 accessToken
   ```java
   validCall.getParams().put("userId", userId);
   validCall.getParams().put("accessToken", token);
   ```
3. **检索服务层**：knowledge-search 根据 userId 过滤 `doc_acl` 表，只返回有权限的文档

### 4.2 防幻觉工具调用

LLM 可能"幻觉"出不存在的工具名或参数。防御措施：

```java
// 白名单过滤：只允许已注册的工具
if (!toolRegistry.contains(raw.getName())) {
    log.warn("幻觉工具调用: {}", raw.getName());
    return null;
}
// 参数裁剪：移除 LLM 幻觉加的多余参数
params.keySet().retainAll(allowed);
```

---

## 五、两个工具的职责

### 5.1 knowledge_search（知识检索）

```json
{
  "name": "knowledge_search",
  "description": "在知识库中检索文档，返回相关片段",
  "parameters": {
    "query": "检索关键词",
    "topK": 5
  }
}
```

实际调用 `GET /search?keyword=xxx&pageNum=1&pageSize=5`，带上用户的 Authorization Token，只返回用户有权查看的结果。

### 5.2 list_knowledge_tree（目录查询）

```json
{
  "name": "list_knowledge_tree",
  "description": "查询知识库的目录结构",
  "parameters": {
    "parentId": null
  }
}
```

调用 `GET /doc/category/tree`，返回分类树。当用户问"有哪些分类"或"某个分类下有什么文档"时使用。

---

## 六、DeepSeek Function Calling

### 6.1 为什么选 DeepSeek

- API 完全兼容 OpenAI 格式（`/v1/chat/completions`）
- 原生支持 Function Calling（`tools` + `tool_choice: "auto"`）
- 中文能力强，价格低
- 不需要翻墙，国内直接访问 `api.deepseek.com`

### 6.2 请求格式

```json
{
  "model": "deepseek-chat",
  "messages": [...],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "knowledge_search",
        "description": "在知识库中检索文档...",
        "parameters": { "type": "object", "properties": {...} }
      }
    }
  ],
  "tool_choice": "auto"
}
```

### 6.3 两种响应

**LLM 直接回答**（不需要调工具）：
```json
{
  "choices": [{
    "finish_reason": "stop",
    "message": { "content": "你好！有什么可以帮你的？" }
  }]
}
```

**LLM 发起工具调用**：
```json
{
  "choices": [{
    "finish_reason": "tool_calls",
    "message": {
      "tool_calls": [{
        "id": "call_xxx",
        "function": {
          "name": "knowledge_search",
          "arguments": "{\"query\":\"请假流程\"}"
        }
      }]
    }
  }]
}
```

---

## 七、嵌入式向量化流水线（knowledge-embedding）

### 7.1 为什么需要向量化

当前的检索基于 Elasticsearch 的关键词匹配（BM25），对同义词、语义相似的理解有限。引入向量检索可以实现：
- "年假怎么请" 也能匹配到 "休假申请流程" 的文档
- 跨语言的语义搜索

### 7.2 流水线流程

```
文档创建/更新
    │
    ▼
RabbitMQ (doc.create / doc.update)
    │
    ▼
DocEmbedConsumer.handleDocEvent()
    │
    ├─ ChunkService.split()
    │     └─ 按字符数+句子边界切分文本
    │
    ├─ EmbeddingService.embed()
    │     └─ 调用 DeepSeek /v1/embeddings → 1536维向量
    │
    └─ DocChunkRepository.save()
          └─ 写入 ES knowledge_chunks 索引
```

### 7.3 分块策略

```
原文: "根据公司规定，员工每年享有5天年假。请假需提前3天在OA系统提交申请，经直属领导审批通过后方可休假。"

分块1: "根据公司规定，员工每年享有5天年假。请假需提前3天在OA系统提交申请，"
分块2: "请假需提前3天在OA系统提交申请，经直属领导审批通过后方可休假。"
                        ↑ overlap=50 字符重叠
```

为什么 overlap：
- 如果严格按句号切分，可能把一个概念的上下文切断
- overlap 让相邻 chunk 有部分重叠，减少信息断裂

### 7.4 向量维度

DeepSeek `text-embedding-3-small` 输出 1536 维向量。ES 索引需要手动创建 mapping：

```json
PUT /knowledge_chunks
{
  "mappings": {
    "properties": {
      "embedding": { "type": "dense_vector", "dims": 1536 }
    }
  }
}
```

### 7.5 当前状态

向量嵌入流水线已实现（chunking + embedding + ES 写入）。混合检索（关键词 + 向量）的搜索端点尚未实现，标记为 TODO。生产环境还需：
- 死信队列：embedding API 调用失败时的重试机制
- 批量处理：大量文档更新时改为批量 embedding
- 增量更新：只重新 embed 变更的 chunks

---

## 八、Redis 会话记忆

### 8.1 为什么不用数据库

Agent 对话是多轮的（用户问 → LLM调工具 → LLM回答），需要记住当前会话的上下文。但这不是持久化需求——用户关掉页面就不需要了。

Redis 是自然选择：
- TTL 自动过期（30分钟），不用写清理逻辑
- 读写快，不影响对话延迟
- 只保留最近 20 条消息（太长会超出 LLM 上下文窗口）

### 8.2 存储格式

```
Key:   agent:session:{sessionId}
Value: [{"role":"user","content":"..."},{"role":"tool","content":"..."}]
TTL:   30 分钟
```

每次对话结束（Agent 给出最终回答）后，把 assistant 的回答追加到历史，重置 TTL。

---

## 九、上下文截断

LLM 的上下文窗口有限（DeepSeek 支持 64K，但 Token 越多越慢越贵）。AgentOrchestrator 在每轮工具调用后检查总字符数：

```java
while (totalChars > maxContextChars && messages.size() > 2) {
    messages.remove(1);  // 跳过 system message，从最早的用户消息开始删
}
```

> TODO: 目前按字符数粗略估计，生产环境应接入 Tokenizer（如 DeepSeek 的 tokenizer）精确计算 Token 数。

---

## 十、关键面试知识点

1. **RAG 和微调（Fine-tuning）的区别？**
   - RAG：不改模型参数，检索外部知识注入 prompt。知识可实时更新，可溯源
   - 微调：修改模型权重，让模型"记住"知识。知识固化在参数中，更新需要重新训练
   - 企业知识库场景 RAG 更合适——知识频繁更新，且需要权限控制

2. **Function Calling 和普通 RAG 的区别？**
   - 普通 RAG：每次提问都检索 + 回答（固定流程）
   - Function Calling：LLM 自己决定是否检索、检索什么、检索几次
   - Agent 能处理多步推理："先查目录看有哪些分类，再检索具体分类下的文档"

3. **为什么工具调用要注入 userId/token 而不是让 LLM 传？**
   - LLM 不知道当前用户是谁——它只能看到你塞进 prompt 的信息
   - 如果让 LLM 自由传 userId，它可能"幻觉"出别人的 userId，绕过权限
   - 服务端强制注入，保证权限不会被 prompt 注入绕过

4. **文本分块为什么要有 overlap？**
   - 语义单元可能跨句子边界："根据第3条规定。员工享有年假。"——这是完整的一句话
   - 如果刚好在"第3条规定"后切断，下一块开头就是"员工享有年假"，缺乏主语
   - overlap 让相邻 chunk 有语境冗余，检索时不容易漏掉

5. **ES 的 dense_vector 和传统关键词检索的区别？**
   - 关键词检索（BM25）：精确匹配词项，快但对同义词/近义词不敏感
   - 向量检索（kNN）：用余弦相似度找语义相近的文本，"请假"能找到"休假申请"
   - 混合检索（Hybrid）：先关键词 + 向量各自查 Top-N，再融合排序，兼顾精准和语义

6. **搜索结果怎么做权限隔离？**
   - 不能直接返回 ES 中所有匹配文档——需要先查 `doc_acl` 表拿到用户可见的 docId 列表
   - ES 查询时用 `terms filter` 对 docId 做预过滤：`filter: { terms: { docId: [1,3,7] } }`
   - 搜索服务通过 Feign 调用 doc 服务获取可见 ID，Feign 失败时 fallback 返回空列表（安全优先）
   - 这是典型的多级权限模型：Gateway 验 JWT → doc 服务管 ACL → search 服务做预过滤

---

## 十一、审计后修复记录（2026-08-11）

### 修复1：搜索服务权限过滤

**问题**：SearchServiceImpl 直接查 ES 返回所有匹配文档，完全绕过 doc_acl 表。

**修复**：
- DocAclService 新增 `getVisibleDocIds(userId)`：合并自己创建的文档 + ACL 授权可读的文档
- DocInfoController 新增 `GET /doc/visible-ids` 端点供搜索服务 Feign 调用
- SearchServiceImpl 搜索时先获取可见 ID 列表，用 ES `terms filter` 预过滤
- 搜索模块新增 Feign client + Fallback（失败时返回空列表，安全优先）

### 修复2：前端 Agent 智能问答页面

**问题**：整个 RAG Agent 后端已实现，但前端无任何入口。

**修复**：
- 新增 `AgentChat.vue`：对话式 UI，支持思考链可视化（展开查看每轮工具调用）
- Vite 代理添加 `/agent` 路由
- 侧边栏新增「🤖 AI 问答」入口

### 修复3：UserContextUtil 提取到公共模块

**问题**：`knowledge-doc` 和 `knowledge-file` 两个模块各有一份完全相同的 UserContextUtil。

**修复**：提取到 `knowledge-common/src/.../config/UserContextUtil.java`，两个模块删除本地副本，导入统一版本。同时新增 `getCurrentUserInfo()` 方法供 Feign 调用时透传用户头。

### 修复4：前端文档创建支持文件上传

**问题**：FileController 有完善的文件上传 API，但前端创建文档时只能填文本内容。

**修复**：DocList.vue 新建/编辑弹窗中新增 `el-upload` 组件，保存时先上传文件到 `/file/upload`，再将返回的文件信息（fileUrl、fileSize、fileType 等）一并提交到 `/doc`。

### 修复5：测试数据与注册流程

**问题**：init.sql 只有一个 admin 用户，无法演示多用户 ACL 权限隔离。

**修复**：
- SQL 新增测试普通用户 zhangsan / user123，并分配 USER 角色
- 前端新增 `Register.vue` 注册页面
- 登录页添加注册链接和测试账号提示

### 当前已知 TODO（不影响基本运行）

| 项目 | 说明 |
|---|---|
| ES `knowledge_chunks` mapping | 需手动 PUT 创建 dense_vector(1536) |
| 混合检索端点 | 向量 + 关键词融合搜索未实现 |
| Tokenizer 精确计数 | Agent 上下文截断目前用字符数估算 |
| Embedding 死信队列 | API 调用失败降级返回零向量 |
| 前端文件在线预览 | FileController 有 `/file/preview/{id}`，前端未对接 |

### 修复6：ES Date Format 导致搜索 500（2026-08-11）

**问题**：搜索服务报 `Unable to convert value '2026-07-01' to java.time.LocalDateTime for property 'createTime'`。ES `knowledge_doc` 索引在 `@Field(format = DateFormat.date_hour_minute_second)` 注解添加前创建，存储的是纯日期 `2026-07-01`，但 Java 实体期望 `yyyy-MM-dd'T'HH:mm:ss` 格式。

**根因**：注解是后来加上的，旧索引 mapping 只有 `"type": "date"` 无 format 约束，ES 接受纯日期写入。Spring Data ES 读回时按 `date_hour_minute_second` 格式解析失败。

**修复**：
- 删除 `knowledge_doc` 索引 → Spring Data ES 重启后以正确 mapping 重建
- 手动 PUT mapping 设置 `"format": "yyyy-MM-dd'T'HH:mm:ss"`
- 通过 ES API 回填已有文档（createTime 补全时间部分）
- 新增 `POST /search/admin/reindex` 运维端点，接收文档列表批量重建索引，避免日后手动 curl ES

### 修复7：Embedding 服务 OOM（2026-08-11）

**问题**：`knowledge-embedding` 服务处理 MQ 消息时爆 `OutOfMemoryError: Java heap space`，crash 在 ES `httpasyncclient` I/O reactor 线程。

**根因**：
1. `EmbeddingOrchestrator` 逐 chunk 调用 `chunkRepository.save()`，每个 chunk 的 `float[1536]` 向量序列化为 JSON 约 25KB，N 个 chunk 就有 N 次 HTTP 往返 + N 次 JSON 序列化
2. ES `Dense_Vector` 字段的 JSON 体积远超普通字段，放大内存压力
3. 没有显式 JVM 堆参数，默认堆不足以承载 dense_vector 索引负载

**修复**：
- `EmbeddingOrchestrator`：逐条 save → `saveAll()` 批量写入，减少 HTTP 往返和重复序列化
- `pom.xml`：spring-boot-maven-plugin 添加 `-Xms256m -Xmx512m` JVM 参数
- `application.yml`：ES 客户端添加 `connection-timeout: 5s` + `socket-timeout: 30s`，防止连接堆积
- 搜索服务同步加上 ES timeout 配置

**面试要点**：当被问"遇到过什么性能问题"，可以用这个例子：dense_vector 序列化开销大 + 逐条写 ES 导致的内存压力，改成批量写入 + 合理 JVM 参数后解决。展示了对 JVM 内存管理和 ES 写入优化的理解。
