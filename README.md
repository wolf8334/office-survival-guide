# 办公室生存指南

#### 介绍
基于Spring AI的大模型研究，目前专注于提高RAG的准确率。

#### 软件架构
---
v2版本 在V1版本的基础上完善功能

支持多种向量库的实现，包括Qdrant，Milvus，PGVector，默认Qdrant

NL2SQL功能可选，默认不启动

完善代码结构，整理功能实现

增加上下文窗口扩展支持，构建完整的向量搜索、BM25、RRF、重排序、上下文窗口扩展的RAG增量功能。

---
v1版本

已实现基于Spring Boot 3.5.11和Spring AI 1.1.3的框架搭建。

已实现多模型切换及多数据源。

已实现基于PGVector的向量搜索和分析。

已实现基于知识库的RAG，实现知识库动态更新及加载。

已实现基于MySQL的NL2SQL简单查询实现，可扩展至PG等数据库。

已实现文件上传及解析内容，支持查询

已实现Qdrant适配，支持Qwen/Qwen3-Embedding-8B

已实现Rerank增强，支持Qwen/Qwen3-Reranker-8B

完善流式页面展示效果