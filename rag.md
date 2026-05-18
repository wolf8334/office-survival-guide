# RAG 层级索引检索优化

## 背景

文档向量化阶段已经实现了层级索引（章/节/小节三级结构），每个 chunk 都携带了丰富的元数据：

| 元数据字段 | 类型 | 说明 |
|-----------|------|------|
| `hierarchyPath` | `List<String>` | 层级路径，如 `["node_1", "node_5", "node_12"]` |
| `nodeId` | `String` | 当前节点的唯一 ID，如 `node_550e8400-e29b-...` |
| `level` | `int` | 层级：0=章，1=节，2=小节 |
| `isAbstract` | `boolean` | 是否为章节摘要 chunk（true=摘要层） |
| `section` | `String` | 当前节点标题 |

但 RAG 检索阶段（`LLMUtil.vectorSearch`）完全没有利用这些层级信息，只做单层向量相似度搜索。

## 修改目标

1. **扩展 `VectorStoreUtil`**：支持多条件 `Filter.Expression` 组合查询（OR / AND / IN / EQ）
2. **修改 `LLMUtil.vectorSearch`**：实现两阶段检索——先查摘要层定位章节，再在相关章节内检索
3. **优化 `hierarchyPath` 存储格式**：从逗号分隔字符串改为 `List<String>` 数组，利用 Qdrant 的 `IN` 操作符

---

## 修改内容

### 1. `VectorStoreUtil.java` — 多条件组合

#### 新增方法

```java
// 核心方法：直接接受 Filter.Expression
public List<Document> similaritySearch(String requirement, int topk, double threshold,
                                        Filter.Expression filterExpression)
```

#### 便捷构建器方法

| 方法 | 说明 | 示例 |
|------|------|------|
| `eq(key, value)` | 等值条件 | `eq("isAbstract", true)` → `isAbstract == true` |
| `in(key, values...)` | IN 条件 | `in("level", 0, 1)` → `level IN [0, 1]` |
| `or(key, List<?>)` | 同字段多值 OR（底层用 IN） | `or("filename", List.of("a.pdf", "b.pdf"))` |
| `or(Expression...)` | 多表达式 OR | `or(eq("isAbstract", true), eq("level", 0))` |
| `and(Expression...)` | 多表达式 AND | `and(fileFilter, levelFilter, notAbstract)` |

#### 向后兼容

原有的 `similaritySearch(query, topk, threshold, String filter)` 保持可用，内部委托到新的 `Filter.Expression` 版本：

```java
// 旧用法：用 type 字段过滤
vectorStore.similaritySearch(query, 10, 0.5, "知识库文件导入");

// 新用法：多条件组合
var f = vectorStore.and(
    vectorStore.eq("isAbstract", false),
    vectorStore.or("level", List.of(0, 1))
);
vectorStore.similaritySearch(query, 20, 0.5, f);
```

### 2. `PDFUtil.java` — 存储格式优化

```java
// 修改前：逗号分隔字符串，Qdrant 无法做数组查询
doc.getMetadata().put("hierarchyPath", String.join(",", node.getHierarchyPath()));

// 修改后：直接存 List<String>，Qdrant IN 操作符可以匹配
doc.getMetadata().put("hierarchyPath", node.getHierarchyPath());
```

### 3. `LLMUtil.vectorSearch()` — 两阶段检索

#### 检索流程

```
用户查询
  │
  ▼
┌─────────────────────────────────┐
│ 阶段1：检索摘要层                │
│ isAbstract == true，topK = 5    │
│ 定位相关章节                     │
└──────────────┬──────────────────┘
               │
     ┌─────────┴──────────┐
     │ 有结果              │ 无结果
     ▼                    ▼
 提取 hierarchyPath   降级为普通检索
 中相关 nodeId          （无 filter）
     │
     ▼
┌─────────────────────────────────┐
│ 阶段2：在相关章节内检索          │
│ isAbstract == false             │
│ hierarchyPath IN [相关 nodeId]   │
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│ 合并结果 + 去重                  │
│ limit topk                      │
└──────────────┬──────────────────┘
               │
               ▼
      RRF 融合 → Rerank → 上下文扩展 → 返回
```

#### 关键设计决策

- **第一阶段 topK=5**：摘要层数量本身很少，5 条足够覆盖相关章节
- **降级策略**：摘要层无结果时回退到普通检索，不影响存量文档的检索
- **结果合并去重**：同一 chunk 可能同时出现在摘要层和内容层，按 document id 去重
- **保留原有管线**：RRF 融合、Rerank 重排序、MySQL 完整内容取回均完整保留

### 4. `example/VectorStoreExamples.java` — 使用示例

#### 示例 1：单条件查询

```java
var filter = vectorStoreUtil.eq("isAbstract", true);
var docs = vectorStoreUtil.similaritySearch(query, 10, 0.5, filter);
```

#### 示例 2：跨文件 OR

```java
var fileFilter = vectorStoreUtil.or("filename",
    List.of("财务制度.pdf", "人事手册.pdf", "行政规范.pdf"));
var docs = vectorStoreUtil.similaritySearch(query, 15, 0.5, fileFilter);
```

#### 示例 3：跨层级 OR

```java
// 查询章(level=0)或节(level=1)
var levelFilter = vectorStoreUtil.or("level", List.of(0, 1));
var docs = vectorStoreUtil.similaritySearch(query, 20, 0.5, levelFilter);
```

#### 示例 4：多条件 AND

```java
var fileFilter = vectorStoreUtil.or("filename", List.of("财务制度.pdf", "报销规范.pdf"));
var levelFilter = vectorStoreUtil.or("level", List.of(0, 1));
var notAbstract = vectorStoreUtil.eq("isAbstract", false);

var combined = vectorStoreUtil.and(fileFilter, levelFilter, notAbstract);
var docs = vectorStoreUtil.similaritySearch(query, 20, 0.5, combined);
```

#### 示例 5：完整两阶段检索

```java
// 阶段1：检索摘要层
var abstractFilter = vectorStoreUtil.eq("isAbstract", true);
var abstractDocs = vectorStoreUtil.similaritySearch(query, 5, 0.5, abstractFilter);

// 提取相关路径
var allNodeIds = abstractDocs.stream()
    .map(doc -> (List<String>) doc.getMetadata().get("hierarchyPath"))
    .flatMap(List::stream)
    .distinct()
    .toList();

// 阶段2：在相关章节内检索
var pathFilter = vectorStoreUtil.or("hierarchyPath", allNodeIds);
var contentFilter = vectorStoreUtil.eq("isAbstract", false);
var finalFilter = vectorStoreUtil.and(pathFilter, contentFilter);

var contentDocs = vectorStoreUtil.similaritySearch(query, 20, 0.5, finalFilter);

// 合并去重
var allDocs = new ArrayList<>(abstractDocs);
allDocs.addAll(contentDocs);
```

---

## 技术细节

### Spring AI FilterExpressionBuilder 支持的操作符

基于 Spring AI 1.1.4 的 `FilterExpressionBuilder`：

| 操作符 | 方法 | 说明 |
|--------|------|------|
| `==` | `eq(key, value)` | 等于 |
| `!=` | `ne(key, value)` | 不等于 |
| `>` | `gt(key, value)` | 大于 |
| `>=` | `gte(key, value)` | 大于等于 |
| `<` | `lt(key, value)` | 小于 |
| `<=` | `lte(key, value)` | 小于等于 |
| `IN` | `in(key, values...)` | 在列表中 |
| `NIN` | `nin(key, values...)` | 不在列表中 |
| `AND` | `and(left, right)` | 逻辑与 |
| `OR` | `or(left, right)` | 逻辑或 |
| `NOT` | `not(op)` | 逻辑非 |
| `IS NULL` | `isNull(key)` | 为空 |
| `IS NOT NULL` | `isNotNull(key)` | 不为空 |
| `( )` | `group(op)` | 分组 |

### 涉及的源文件

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `util/VectorStoreUtil.java` | 扩展 | 新增 Filter.Expression 重载 + 便捷方法 |
| `util/PDFUtil.java` | 修改 | hierarchyPath 存储格式改为 List |
| `util/LLMUtil.java` | 重构 | vectorSearch 实现两阶段检索 |
| `example/VectorStoreExamples.java` | 新增 | 5 个完整使用示例 |
