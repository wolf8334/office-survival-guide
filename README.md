# 办公室生存指南

## 介绍
基于Spring AI的大模型研究，目前专注于提高RAG的准确率。

## TODO
- HyDE (Hypothetical Document Embeddings)：先让 LLM 根据问题生成一个“伪答案”，然后用这个“伪答案”去库里搜。因为“答案搜答案”的向量距离通常比“问题搜答案”更近。

- Multi-Query (多查询并行)：让 LLM 将用户的原始问题改写成 3-5 个意思相近但侧重点不同的问题，全部送去检索，最后把结果汇总。这能极大提升召回率（Recall）。

- Query Decomposition (查询拆分)：如果用户问的是复合问题（比如“对比 A 和 B 的技术参数”），先拆成两个子问题分别检索，再汇总。

- LLMLingua (上下文压缩)：使用专门的小模型计算 Token 的信息熵，删掉文档中不重要的助词、修饰词，甚至不相关的段落，在保留核心语义的前提下减少 50%-80% 的体积。

- Self-RAG (自省式检索)：让模型判断检索到的内容是否真的有用。如果没用，模型会主动要求重新检索或扩大搜索范围，而不是强行解释。

- Context Reordering (长上下文重排)：研究表明，LLM 对列表开头和结尾的信息感知最强（Lost in the Middle 现象）。把 Rerank 分数最高的最核心内容放在 Context 的最开头和最末尾，中间放辅助背景。

- GraphRAG：提取文档中的实体和关系构建图谱。检索时，先定位实体，再沿着边找到关联知识。微软最近开源的方案证明了这在处理复杂文档集时比普通 RAG 强得多。

- 链路评估 (RAGAS / TruLens)

* 建立一套自动化的黄金题库 (Ground Truth)。

  * 使用 RAGAS 框架，通过 LLM 自动打分，评估四个核心指标：

    1.忠实度 (Faithfulness)：回答是否来源于检索内容。

    2.相关性 (Answer Relevance)：回答是否切题。

    3.上下文精度 (Context Precision)：搜到的东西是不是真的有用。

    4.上下文召回率 (Context Recall)：该搜到的搜到没有


## 已完成功能

---
v2版本 在v1版本的基础上完善功能

- 支持多种向量库的实现，包括Qdrant，Milvus，PGVector，默认Qdrant
- NL2SQL功能可选，默认不启动
- 完善代码结构，整理功能实现
- 增加上下文窗口扩展支持，构建完整的向量搜索、BM25、RRF、重排序、上下文窗口扩展的RAG增量功能。
- 增加代码生成示例，基于SpringBoot2及Vue生成代码，根据生成结果微调

---
v1版本

- 已实现基于Spring Boot 3.5.11和Spring AI 1.1.3的框架搭建。
- 已实现多模型切换及多数据源。
- 已实现基于PGVector的向量搜索和分析。
- 已实现基于知识库的RAG，实现知识库动态更新及加载。
- 已实现基于MySQL的NL2SQL简单查询实现，可扩展至PG等数据库。
- 已实现文件上传及解析内容，支持查询
- 已实现Qdrant适配，支持Qwen/Qwen3-Embedding-8B
- 已实现Rerank增强，支持Qwen/Qwen3-Reranker-8B
- 完善流式页面展示效果