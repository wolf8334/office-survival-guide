# Office Survival Guide — 技术架构说明

> 基于 Spring AI + Spring Boot 的企业级 RAG（检索增强生成）问答平台，集知识库管理、AI 代码生成、意图识别、流式问答于一体。

---

## 一、项目概览

| 属性 | 说明 |
|------|------|
| **项目名** | `officeSurvivalGuide` |
| **包路径** | `com.xhr.springai.officeSurvivalGuide` |
| **版本号** | `2.0.0-SNAPSHOT` |
| **构建工具** | Maven |
| **JDK 版本** | Java 26（启用 Preview 特性） |
| **核心框架** | Spring Boot 3.5.11 + Spring AI 1.1.4 |
| **部署产物** | `osg-service-2.0.0-SNAPSHOT.jar` |

---

## 二、目录结构

```
office-survival-guide/
├── pom.xml                                         # Maven 构建文件
├── src/main/java/com/xhr/springai/officeSurvivalGuide/
│   ├── OfficeSurvivalGuideApplication.java         # Spring Boot 启动入口
│   ├── bean/                                       # POJO / DTO
│   │   ├── Result.java                             # 统一响应封装
│   │   ├── CommonData.java                         # 通用数据载体
│   │   ├── GenerateCode.java                       # 代码生成请求体
│   │   ├── GenerateResult.java                     # 代码生成结果体
│   │   ├── SqlIr.java / FileItem.java / RerankResult.java 等
│   ├── config/                                     # 配置层
│   │   ├── AIConfig.java                           # ChatClient Bean 注册
│   │   ├── BaseAiProperties.java                   # AI 属性基类（URL/Key/Model/Params）
│   │   ├── ChaterProperties.java                   # 对话模型配置（qwen3.5-plus）
│   │   ├── CoderProperties.java                    # 代码模型配置（qwen3.5-397b-a17b）
│   │   ├── VLProperties.java                       # 视觉模型配置（DeepSeek-OCR）
│   │   ├── IntentClassification.java               # 意图分类数据结构
│   │   └── enums/                                  # 枚举定义
│   │       ├── INTENT.java                         # 主意图枚举
│   │       ├── TARGET_TYPE.java                    # 对象类型
│   │       ├── TIME_SCOPE.java                     # 时间范围
│   │       ├── ANSWER_STYLE.java                   # 回答风格
│   │       └── Semantic.java                       # 语义路由
│   ├── client/                                     # AI 客户端（HTTP 封装层）
│   │   ├── AIClient.java                           # 通用 AI HTTP 客户端（同步 + SSE 流式）
│   │   ├── AIRequest.java                          # 请求体 Builder（模型/Key/消息/参数）
│   │   ├── AIResponse.java                         # 响应体（content/usage/finishReason）
│   │   ├── ChaterClient.java                       # 对话客户端（代理 → AIClient）
│   │   ├── CoderClient.java                        # 代码生成客户端（代理 → AIClient）
│   │   └── VLClient.java                           # 视觉识别客户端
│   ├── service/                                    # 业务逻辑层
│   │   ├── HQService.java                          # 后勤知识库：RAG 问答、文件向量化
│   │   ├── CodeService.java                        # 代码生成：需求分析→编码→ZIP打包
│   │   ├── OSGService.java                         # 办公室黑话翻译
│   │   ├── KnowledgeService.java                   # 专家知识库 CRUD + 表结构向量化
│   │   ├── RerankService.java                      # Rerank 重排序、Tika 文档解析、切块、MySQL 存储
│   │   ├── QdrantMetaService.java                  # Qdrant 元数据查询（已索引文件名列表）
│   │   ├── TempStorage.java                        # Redis 临时存储（代码 ZIP 缓存）
│   │   └── TokenBucketService.java                 # Bucket4j 限流器
│   ├── utility/                                    # 工具类/管道编排
│   │   ├── ProgressUtil.java                       # RAG 核心工作流（意图路由→知识/SQL 分支）
│   │   ├── LLMUtil.java                            # LLM 调用封装（向量检索、BM25、RRF 融合、重排序）
│   │   ├── VectorStoreUtil.java                    # Spring AI VectorStore 代理（Qdrant）
│   │   ├── SemanticRouting.java                    # 语义路由（意图分拣）
│   │   ├── JSONUtil.java                           # JSON 序列化/清理
│   │   ├── SqlParamExtractor.java                  # JSQLParser SQL 解析
│   │   ├── CodeSplitter.java                       # 代码拆封（按文件名分离）
│   │   ├── PDFUtil.java                            # PDF 特殊处理
│   │   ├── DBUtil.java                             # DDL 查询 & 示例数据提取
│   │   ├── RedisUtil.java                          # Redis 封装
│   │   ├── ExcelUtil.java                          # Excel 读取
│   │   ├── Chater.java / VLChater.java             # 对话/视觉包装器
│   ├── controller/                                 # REST 控制器
│   │   ├── HQController.java                       # 后勤问答 / 文件入库（路由: /hqdmx）
│   │   ├── CodeController.java                     # AI 代码生成（路由: /coder）
│   │   ├── OSGController.java                      # 办公室黑话翻译（路由: /api/office）
│   │   └── KnowledgeController.java                # 专家知识库管理（路由: /knowledges）
│   ├── advisor/
│   │   └── TokenAdvisor.java                       # Token 计费/限流切面
│   └── systemInterface/
│       └── ICaller.java                            # 统一调用接口
└── src/main/resources/
    ├── application.yml                             # 应用配置
    ├── static/                                     # 前端 SPA
    │   ├── index.html / chat.html / code.html / upload.html  # V1（Vue3 + Element Plus）
    │   ├── entry.html / list.html                   # 专家库录入/列表
    │   ├── chat_1.html                              # 旧版问答页
    │   └── *_old.html                               # 所有旧版前端页面
```

---

## 三、依赖版本清单

### Spring 生态

| 依赖 | 版本 | 用途 |
|------|------|------|
| `spring-boot-starter-parent` | **3.5.11** | 父 POM / 自动装配 |
| `spring-boot-starter-web` | (managed) | MVC 控制器 |
| `spring-boot-starter-webflux` | (managed) | WebClient（AI HTTP 调用） |
| `spring-boot-starter-jdbc` | (managed) | JDBC / JdbcTemplate |
| `spring-boot-starter-data-redis` | (managed) | Lettuce Redis 客户端 |
| `spring-boot-starter-actuator` | (managed) | 健康检查 |
| `spring-boot-starter-aop` | (managed) | 切面支持（TokenAdvisor） |

### Spring AI 系列（BOM: 1.1.4）

| 依赖 | 版本 | 用途 |
|------|------|------|
| `spring-ai-starter-model-openai` | **1.1.4** | OpenAI 兼容 API（硅基流动、通义千问等） |
| `spring-ai-starter-model-anthropic` | **1.1.4** | Claude 模型 |
| `spring-ai-client-chat` | **1.1.4** | ChatClient 抽象层 |
| `spring-ai-starter-vector-store-qdrant` | **1.1.4** | Qdrant 向量存储 |
| `spring-ai-tika-document-reader` | **1.1.4** | Tika 文档读取器 |
| `spring-ai-pdf-document-reader` | **1.1.4** | PDF 文档读取 |
| `spring-ai-model-chat-memory-repository-jdbc` | **1.1.4** | JDBC 对话记忆持久化 |

### AI 框架

| 依赖 | 版本 | 用途 |
|------|------|------|
| `langchain4j-core` | **1.12.2** | LangChain4j 核心库（用于语义路由等） |

### 数据存储

| 依赖 | 版本 | 用途 |
|------|------|------|
| `mysql-connector-j` | **9.5.0** | MySQL JDBC 驱动 |
| `HikariCP` | (managed) | Hikari 连接池 |
| `spring-boot-starter-data-redis` | (managed) | Redis 连接（Lettuce） |

### 工具类库

| 依赖 | 版本 | 用途 |
|------|------|------|
| `bucket4j_jdk17-core` | **8.18.0** | Token 限流 |
| `jsqlparser` | **4.9** | SQL 语法解析（抽取 WHERE 条件） |
| `springdoc-openapi-starter-webmvc-ui` | **2.5.0** | Swagger / OpenAPI 文档 |
| `poi-ooxml` / `poi` | **5.5.1** | Excel 文件处理 |
| `tika-core` | **2.9.2** | 文档 MIME 类型检测 |
| `pdfbox` | **3.0.7** | PDF 文件解析 |
| `lombok` | (managed) | 代码简化 |
| `commons-io` | **2.21.0** | IO 工具 |
| `commons-csv` | **1.10.0** | CSV 解析 |
| `commons-pool2` | (managed) | 连接池 |
| `jackson-databind` | (managed) | JSON 序列化 |

---

## 四、AI 模型配置

| 用途 | 模型 | 提供商 | 配置位置 |
|------|------|--------|---------|
| 主要对话 | `claude-opus-4-7` | Anthropic | `spring.ai.anthropic` |
| 闲聊对话 | `qwen3.5-plus-2026-04-20` | 通义千问 | `custom.chat` |
| 代码生成 | `qwen3.5-397b-a17b`（200万 token）| 通义千问 | `custom.code` |
| 嵌入模型 | `Qwen/Qwen3-Embedding-8B`（4096 维）| 硅基流动 | `spring.ai.openai.embedding` |
| 重排序模型 | `Qwen/Qwen3-Reranker-8B` | 硅基流动 | `custom.rerank-name` |
| 视觉 OCR | `DeepSeek-OCR` | 硅基流动 | `custom.vl-name` |

---

## 五、分层架构与调用关系

### 5.1 Controller → Service → Client 调用链路

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 SPA (Vue3)                          │
│  index_v1.html → chat_v1.html  /  code_v1.html  /  upload_v1.html│
└──────────────────────────────┬──────────────────────────────────┘
                               │  HTTP (REST / SSE / Multipart)
┌──────────────────────────────▼──────────────────────────────────┐
│                     Controller 层 (4 个)                        │
│                                                                  │
│  HQController ("/hqdmx")     →  HQService                       │
│  CodeController ("/coder")   →  CodeService                     │
│  OSGController ("/api/office")→  OSGService                     │
│  KnowledgeController ("/knowledge") → KnowledgeService          │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                      Service 层 (8 个)                          │
│                                                                  │
│  HQService        → ProgressUtil (RAG 工作流)                   │
│                   → RerankService (文档解析 / 分块)             │
│                   → VectorStoreUtil (Qdrant 操作)               │
│                                                                  │
│  CodeService      → TempStorage (Redis ZIP 缓存)                │
│                   → CodeSplitter (代码拆封)                     │
│                   → SqlParamExtractor (SQL 解析)                │
│                   → ChaterClient / CoderClient (AI 调用)        │
│                                                                  │
│  KnowledgeService → VectorStoreUtil (表结构向量化)              │
│                   → ChaterClient (AI 调用)                      │
│                                                                  │
│  OSGService       → ChaterClient (黑话翻译)                     │
│                                                                  │
│  RerankService    → HTTP RestClient (Qwen Rerank API)           │
│                   → TikaDocumentReader (文档解析)               │
│                   → TokenTextSplitter (文本分块)                │
│                   → JdbcTemplate (MySQL 存储)                   │
└────┬──────────────────┬───────────────────┬──────────────────────┘
     │                  │                   │
┌────▼──────┐  ┌───────▼────────┐  ┌──────▼────────┐
│ Client 层  │  │  VectorStore   │  │   Storage     │
│            │  │  (Qdrant)     │  │  MySQL/Redis  │
│ AIClient   │  │  Spring AI     │  │  (JdbcTemplate│
│   ↑        │  │  VectorStore  │  │   RedisTempl) │
│ Chater/    │  │  Proxy        │  │               │
│ Coder/     │  │               │  │               │
│ VLClient   │  │               │  │               │
└────┬──────┘  └───────────────┘  └───────────────┘
     │
     ▼ (HTTP / SSE)
┌─────────────────────────────┐
│  外部 LLM API               │
│  Anthropic / 硅基流动 / 通义 │
└─────────────────────────────┘
```

### 5.2 核心 Bean 注入关系

```
AIClient (WebClient)
  ├── ChaterClient  → 对话模型（qwen3.5-plus）
  ├── CoderClient   → 代码模型（qwen3.5-397b-a17b）
  └── VLClient      → 视觉模型（DeepSeek-OCR）

AIConfig 注册 Bean:
  ├── rerankClient   → OpenAI ChatClient（Qwen-Embedding-8B）
  ├── vlClient       → OpenAI ChatClient（DeepSeek-OCR）
  ├── claudeClient   → Anthropic ChatClient（claude-opus-4-7）
  ├── chatMemory     → MessageWindowChatMemory（JDBC 持久化）
  └── chatMemoryRepo → JdbcChatMemoryRepository

ProgressUtil 核心管道:
  SemanticRouting → LLMUtil.vectorSearch → VectorStoreUtil → Qdrant
              → LLMUtil.BM25Search  → MySQL Full-Text
              → LLMUtil.rrfCombine  → RRF 融合排名
              → RerankService.rerank → Qwen Reranker 重排
              → RerankService.getFullDocument → MySQL 上下文扩展
              → ChaterClient.callFlux → SSE 流式输出
```

---

## 六、功能模块详解

### 6.1 RAG 知识问答（核心功能）

**入口**: `POST /hqdmx/acknowledge` → `HQController.acknowledge()`
- **协议**: Server-Sent Events (text/event-stream)
- **请求体**: `{ "userRequirement": "用户问题" }`

**处理管线**: `ProgressUtil.processMessage()`

```
用户问题
  │
  ├─ ① SemanticRouting.routeSemantic()        意图分拣（LLM 辅助）
  │     └─ Prompt 注入专家知识库规则
  │     └─ 输出 IntentClassification（intent / target_type / time_scope / answer_style）
  │
  ├─ ② 根据意图分流
  │     ├─ 知识类 (reason_analysis / progress_status / ...)
  │     │     └─ knowledge() → LLM 提炼 → 向量检索 → RRF 融合 → Rerank → 完整上下文 → 流式回答
  │     └─ 统计类 (count_stat)
  │           └─ writeSQL() → LLM 识别表名 → DDL 查询 → 生成 SQL → 执行 → SQL 结果 → LLM 总结
  │
  └─ ③ 流式返回 SSE 到前端
```

**RAG 检索增强**: `LLMUtil.vectorSearch()`

```
用户提问（LLM 提炼后）
  │
  ├─ 向量检索 → VectorStoreUtil.similaritySearch() → Qdrant（Top-K = 10, threshold = 0.5）
  │     └─ 返回 Document 列表（含文本 + metadata）
  │
  ├─ BM25 检索 → MySQL Full-Text Search（Top-50）
  │     └─ knowledge_chunks 表内容匹配
  │
  ├─ RRF 融合 → LLMUtil.rrfCombine()
  │     └─ 将向量路 + BM25 路排名用 Reciprocal Rank Fusion 融合（k = 60）
  │     └─ 取 Top-15
  │
  ├─ Rerank 重排 → RerankService.rerank()
  │     └─ 调用 Qwen3-Reranker-8B API（restClient POST /v1/rerank）
  │     └─ 取 Top-5 高相关度文档
  │
  └─ 上下文扩展 → RerankService.getFullDocument()
        └─ 根据 filename + pageNum 从 MySQL 取完整文档（含相邻切块）
        └─ 拼接为最终 context 送入 LLM
```

### 6.2 AI 代码生成

**流程**: 两步走（分析 → 生成）

| 步骤 | 端点 | 方法 | 说明 |
|------|------|------|------|
| ① 分析 | `POST /coder/generate/analyze` | `CodeService.analyze()` | LLM 判断需求完整性 |
| ② 生成 | `POST /coder/generate/code` | `CodeService.writeFullCode()` | LLM 输出完整代码 |
| ③ 下载 | `GET /coder/generate/download?token=` | `CodeService.download()` | 下载 ZIP |
| ④ 单文件 | `GET /coder/generate/downloadFile?token=&name=` | `CodeService.downloadFile()` | 从 ZIP 解压单文件 |

**生成管线**:

```
前端发送需求 → analyze()
  │
  ├─ ChaterClient.call() → LLM 判断可否生成
  │     ├─ 可生成 → 返回 summary + canGenerate:true
  │     └─ 不可生成 → 返回 reason + canGenerate:false
  │
  └─ 确认可生成 → writeFullCode()
        ├─ 构造 Prompt（需求 + 技术分析 + 上传参考文件内容）
        ├─ CoderClient.call() → qwen3.5-397b-a17b 生成代码（------文件名 + 内容 格式）
        ├─ CodeSplitter.split() → 按 "------" 分隔符拆分为 Map<文件名, 代码>
        ├─ CodeService.zipFiles() → 内存打包为 ZIP
        └─ TempStorage.put(token, ZIP) → Redis 缓存 24h
        └─ 返回 GenerateResult（文件列表 + 下载 URL + 唯一 token）
```

### 6.3 知识库文件入库

**端点**: `POST /hqdmx/vectorize`（MultipartFile）

**处理管线**: `HQService.vectorize()`

```
接收文件
  │
  ├─ RerankService.tikaReader(file)
  │     ├─ 文件类型检测（Tika MIME detection）
  │     ├─ PDF 走 PDFUtil.readPDF()
  │     └─ 非 PDF 走 TikaDocumentReader.read()
  │     └─ 标注 pageCount + filename + fileType
  │
  ├─ RerankService.split() → TokenTextSplitter
  │     └─ maxToken (500) + minChunkLength (200字符) + 标点切分
  │     └─ 切块为多个 Document（每块 ~500 tokens）
  │
  ├─ JSONUtil.cleanContent() → 清理空白字符
  ├─ 注入 metadata（filename / type）
  │
  ├─ VectorStoreUtil.delete() → 删除同名旧向量（幂等）
  ├─ VectorStoreUtil.add() → 写入 Qdrant（嵌入 4096 维）
  │
  └─ RerankService.addDocumentToMySQL() → 入库 knowledge_chunks 表
        └─ 存储 id / filename / content / metadata(JSON)
```

### 6.4 办公室黑话翻译

**端点**: `POST /api/office/rag`

**调用链**: `OSGController → OSGService → ChaterClient`

将日常口语转写为职场专业话术，使用 ChaterClient 调用对话模型完成文本改写。

### 6.5 专家知识库管理

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /knowledges/addKnowledge` | `KnowledgeService.addKnowledgeBase()` | 新增专家规则/关键字到 sys_expert_rules 表 |
| `GET /knowledges/list` | `KnowledgeService.list()` | 查询专家库关键字列表 |

专家知识在语义路由（SemanticRouting）时被注入到 Prompt 中，辅助意图分类。

---

## 七、前端架构

### 7.1 技术栈

| 层 | 技术 |
|----|------|
| UI 框架 | Vue 3 (Composition API) + Element Plus |
| Markdown 渲染 | Marked.js + DOMPurify |
| 流式接收 | Fetch API + ReadableStream (SSE) |
| 路由模式 | 多页面 SPA（每个功能独立 HTML） |

### 7.2 文件清单

| 文件 | 功能 | 技术栈 |
|------|------|--------|
| `index_v1.html` | 平台入口首页 | Vue3 + ElementPlus |
| `chat_v1.html` | RAG 问答（流式交互） | Vue3 + EP + Marked + DOMPurify |
| `code_v1.html` | 代码智能生成 | Vue3 + EP |
| `upload_v1.html` | 知识库文件上传 | Vue3 + EP |
| `entry.html` | 专家库关键字录入 | 原生 JS |
| `list.html` | 知识库条目列表 | 原生 JS |
| `*_old.html` | 所有旧版前端 | 原生 JS / 内联样式 |

### 7.3 前端 → 后端 API 映射

| 页面 | API 调用 |
|------|---------|
| chat_v1.html | `POST /hqdmx/acknowledge` (SSE), `GET /hqdmx/filenames` |
| code_v1.html | `POST /coder/generate/analyze`, `POST /coder/generate/code`, `/coder/generate/download?token=`, `/coder/generate/downloadFile?token=&name=` |
| upload_v1.html | `POST /hqdmx/vectorize` |
| entry.html | `POST /knowledges/addKnowledge` |
| list.html | `GET /knowledges/list` |

---

## 八、数据存储

| 存储 | 用途 | 访问方式 |
|------|------|---------|
| **MySQL** | 知识库原始文档切片（`knowledge_chunks`）、专家规则（`sys_expert_rules`）、对话记忆、业务数据 | JdbcTemplate + Full-Text（BM25检索） |
| **Qdrant** | 向量存储（文本嵌入向量 + 相似性检索） | Spring AI `QdrantVectorStore` |
| **Redis** | 代码生成 ZIP 临时缓存、语义分类缓存 | RedisTemplate（1 天过期） |

---

## 九、配置说明 (`application.yml`)

| 配置项 | 说明 |
|--------|------|
| `spring.datasource` | MySQL 连接（Hikari 池 5~20） |
| `spring.data.redis` | Redis 连接（Lettuce 池 max 8） |
| `spring.ai.anthropic` | Claude 配置（模型/temperature/top_p/max_tokens） |
| `spring.ai.openai.base-url` | 硅基流动 API 地址（`https://api.siliconflow.cn/`） |
| `spring.ai.vectorstore.qdrant` | Qdrant 向量库地址 |
| `custom.chat` | 通义千问对话配置 |
| `custom.code` | 通义千问代码配置（200万 token 上下文窗口） |
| `custom.embedding-name` | 嵌入模型名 |
| `custom.rerank-name` | 重排序模型名 |
| `custom.vl-name` | 视觉识别模型名 |
| `custom.maxToken` / `chunkSize` | 文本分块参数 |
| `logging.level` | JDBC 日志 INFO，Splitter WARN |

所有敏感配置（密钥/数据库地址）通过环境变量注入。
