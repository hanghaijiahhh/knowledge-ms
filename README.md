# 企业智能知识库平台（knowledge-ms）

基于 **SpringCloud Alibaba 微服务架构**的企业知识库平台，集成 **RAG 智能问答**、全文搜索、文档管理、权限控制、文件水印、操作审计等完整功能。

> 技术栈：Spring Boot 3.5 · Spring Cloud Alibaba 2025 · Nacos · OpenFeign · RabbitMQ · Elasticsearch · Redis · MinIO · MySQL · DeepSeek LLM · BGE Embedding · Vue 3

---

## 功能特性

- **RAG 智能问答**：文档切片 → BGE 向量化 → ES Dense Vector kNN 语义检索 → DeepSeek Function Calling Agent 多轮推理，答案可追溯、附来源引用
- **全文搜索 + 向量检索**：ES BM25 关键词搜索、Dense Vector 语义检索、中文 IK 分词、搜索高亮、Redis ZSet 热搜统计
- **双层权限体系**：RBAC 菜单级权限 + ACL 文档级三粒度（读/写/下载）权限，AOP 声明式权限校验
- **文件管理**：MinIO 对象存储、分片上传、MD5 秒传、图片/PDF 动态水印防泄露
- **事件驱动**：RabbitMQ 异步解耦，文档变更实时同步 ES 全文索引、向量索引与审计日志
- **配置外部化**：敏感配置（数据库密码/API Key/JWT 密钥）抽离到 `.env`，不进 Git

---

## 系统架构

```
前端 (Vue3 + Element Plus) :5173
        │ HTTP (Bearer JWT)
        ▼
Gateway (8080) : Spring Cloud Gateway
  └── AuthFilter：JWT 签名校验 + 过期检查 + 用户信息透传
        │
        ├── auth (8081)     认证授权：JWT 签发 + RBAC
        ├── doc (8082)      文档 CRUD + 版本管理 + 分类树 + ACL
        ├── file (8083)     文件上传 + MinIO + 水印 + 分片
        ├── search (8084)   全文搜索 + 向量检索 + 热搜 + 权限过滤
        ├── audit (8085)    操作审计日志
        ├── embedding (8086) 文档切片 + BGE 向量化 + ES 写入
        └── agent (8087)    LLM 智能问答 + Function Calling
              │
              ▼
基础设施：MySQL · Redis · Elasticsearch · MinIO · RabbitMQ · Nacos
```

### 模块与端口

| 模块 | 端口 | 职责 |
|------|------|------|
| `knowledge-gateway` | 8080 | 统一入口 + JWT 鉴权 |
| `knowledge-auth` | 8081 | 认证授权 + RBAC |
| `knowledge-doc` | 8082 | 文档 CRUD + 版本 + 分类 + ACL |
| `knowledge-file` | 8083 | 文件上传 + 水印 + 分片 |
| `knowledge-search` | 8084 | 全文/向量搜索 + 热搜 |
| `knowledge-audit` | 8085 | 操作审计日志 |
| `knowledge-embedding` | 8086 | 文档切片 + 向量化 |
| `knowledge-agent` | 8087 | LLM 智能问答 |
| `knowledge-ui` | 5173 | 前端（Vue3 + Element Plus） |

---

## 目录结构

```
knowledge-ms
├── knowledge-common/     公共模块：统一响应、异常处理、配置外部化
├── knowledge-gateway/    网关
├── knowledge-auth/       认证服务
├── knowledge-doc/        文档服务
├── knowledge-file/       文件服务
├── knowledge-search/     搜索服务
├── knowledge-audit/      审计服务
├── knowledge-embedding/  嵌入服务
├── knowledge-agent/      AI Agent
├── knowledge-ui/         前端
├── sql/init.sql          数据库初始化脚本
├── docs/                 技术学习文档
└── .env.example          敏感配置模板
```

---

## 快速开始

### 1. 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+（前端）
- MySQL 8.0、Redis、RabbitMQ、Elasticsearch 8.x、MinIO、Nacos 2.x

### 2. 启动基础设施

| 组件 | 端口 | 说明 |
|------|------|------|
| Nacos | 8848 | 注册中心（`startup.cmd -m standalone`） |
| MySQL | 3306 | 执行 `sql/init.sql` 初始化数据库 |
| Redis | 6379 | 缓存 / 会话 / 热搜 |
| RabbitMQ | 5672 | 消息队列 |
| Elasticsearch | 9200 | 全文 + 向量索引 |
| MinIO | 9000/9001 | 对象存储 |

### 3. 配置敏感信息

复制模板并填入真实值：

```bash
cp .env.example .env
# 编辑 .env，填入数据库密码、API Key、JWT 密钥等
```

`.env` 文件已被 `.gitignore` 忽略，各服务启动时通过 `DotenvEnvironmentPostProcessor` 自动加载。

### 4. 启动后端服务

按依赖顺序启动（Gateway 最后）：

```bash
# 1. 认证服务
mvn -pl knowledge-auth spring-boot:run
# 2. 文档服务
mvn -pl knowledge-doc spring-boot:run
# 3. 文件服务
mvn -pl knowledge-file spring-boot:run
# 4. 搜索服务
mvn -pl knowledge-search spring-boot:run
# 5. 嵌入服务
mvn -pl knowledge-embedding spring-boot:run
# 6. 审计服务
mvn -pl knowledge-audit spring-boot:run
# 7. Agent 服务
mvn -pl knowledge-agent spring-boot:run
# 8. 网关（最后启动）
mvn -pl knowledge-gateway spring-boot:run
```

或使用 IDEA 逐个启动各模块的 Application 类。

### 5. 启动前端

```bash
cd knowledge-ui
npm install
npm run dev
```

访问 `http://localhost:5173`，前端通过 Vite 代理转发到 Gateway（8080）。

---

## 项目文档

详细技术文档见 [`docs/`](docs/) 目录：

- [D1-项目骨架与技术选型](docs/D1-项目骨架与技术选型.md)
- [D2-D3-Gateway 网关 + Auth 认证](docs/D2-D3-技术学习文档.md)
- [D4-D6-文档服务](docs/D4-D6-技术学习文档.md)
- [D7-文档级 ACL 权限](docs/D7-技术学习文档.md)
- [D8-D9-MinIO 文件服务](docs/D8-D9-技术学习文档.md)
- [D10-D11a-水印 + 在线预览](docs/D10-D11a-技术学习文档.md)
- [D11b-12-Elasticsearch 搜索服务](docs/D11b-12-Elasticsearch搜索服务.md)
- [D13a-操作审计日志](docs/D13a-操作审计日志.md)
- [D14-15-RAG Agent](docs/D14-15-RAG-Agent-技术学习文档.md)
- [踩坑记录-技术总结](docs/踩坑记录-技术总结.md)
- [项目亮点-简历素材](docs/项目亮点-简历素材.md)
- [知识库平台-完整技术文档](docs/知识库平台-完整技术文档.md)（全部文档汇总）

---

## 克隆项目

```bash
git clone git@github.com:hanghaijiahhh/knowledge-ms.git
```
