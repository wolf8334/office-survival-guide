package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.service.RerankService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LLMUtil {

    private static final Logger log = LoggerFactory.getLogger(LLMUtil.class);

    private final Chater chater;
    private final VectorStoreUtil vectorStore;
    private final EmbeddingModel embeddingModel;
    private final JSONUtil json;
    private final JdbcTemplate jdbcTemplate;

    private final RerankService rerank;


    /***
     * 调用LLM，包含对用户输入的提炼，包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> callWithPurificationAndKnowledge(String requirement, String expansionPrompt) {
        // 1 提炼用户输入 转化成标准查询词
        String afterPurified = purifiy(requirement);

        float[] vector = embeddingModel.embed(afterPurified);
        log.info(Arrays.toString(vector));

        // 2 查询向量库，搜索相关内容
        String knowledgeContext = vectorSearch(afterPurified,10,0.55);

        expansionPrompt = expansionPrompt.formatted(knowledgeContext);

        String translated = chater.call(expansionPrompt, afterPurified);
        log.info("AndKnowledge 大模型回答 {}", translated);
        return Result.success(requirement, translated);
    }

    public String vectorString(String requirement) {
        float[] vector = embeddingModel.embed(requirement);
        return "[" + IntStream.range(0, vector.length)
                .mapToDouble(i -> vector[i]).mapToObj(v -> new BigDecimal(Double.toString(v)).toPlainString())
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * 流式调用AI智能体
     * @param requirement 用户原始输入
     * @param purification 提示词，用于AI智能体整理用户输入
     * @param expansionPrompt 提示词，用于向量查询后给智能体的提示词
     * @param topk topk
     * @param thresold 相似度，0到1
     * */
    public Flux<String> callWithPurificationAndKnowledgeStream(String requirement, String purification,String expansionPrompt,int topk,double thresold) {
        String afterPurified = purifiy(requirement);
        log.info("流式响应 afterPurified {}", afterPurified);

        String vectorResult = vectorSearch(afterPurified,topk,thresold);
        log.debug("流式响应 vectorResult {}", vectorResult);

        return chater.callFlux(vectorResult, expansionPrompt + " " + afterPurified);
    }

    /***
     * 调用LLM，不包含对用户输入的提炼，不包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> call(String requirement, String expansionPrompt) {
        String translated = callForString(expansionPrompt, requirement);
        return Result.success(requirement, translated);
    }

    /***
     * 调用LLM，不包含对用户输入的提炼，不包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public String callForString(String requirement, String expansionPrompt) {
        if (requirement.isBlank()) {
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 分析用户输入
        String translated = filterThinkAnswer(chater.call(expansionPrompt, requirement));

        log.info("大模型回答 {}", translated);
        return translated;
    }

    public String callUserStatement(String requirement) {
        if (requirement.isBlank()) {
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 分析用户输入
        String translated = filterThinkAnswer(chater.call(requirement));

        log.info("大模型回答 {}", translated);

        return translated;
    }

    private String purifiy(String requirement){
        if (requirement.isBlank()) {
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 提炼用户输入 转化成标准查询词
        String purification = """
                角色：专家级意图转换器
                任务：将模糊口语转为数据库检索关键词。
                处理规则：
                1. 归纳同类项：如果用户枚举了多个实例（如：南京、北京），请统一归纳为属性名词（如：地市）。
                2. 三元素提取：
                   - 实体对象（表/业务主体）
                   - 目标指标（统计/查询的字段）
                   - 过滤维度（WHERE 条件的核心）
                3. 严格限制：仅输出 2-3 个核心词，空格分隔，严禁输出枚举值，严禁废话。
                4. 如果输入信息中提到之前SQL执行报错，此信息需要保留。
                
                转换示例：
                    - 输入：“看下苏州、南京、无锡这几个地盘的合同额”
                    - 输出：合同额 地市 区域
                """;
//        String afterPurified = requirement.length() > 21 ? chater.call(purification, requirement) : requirement;
//        log.info("用户抽象问题 '{}'，提取为：{}", requirement, afterPurified);
//
//        return afterPurified;
        return requirement;
    }

    public String vectorSearch(String afterPurified,int topk,double thresold){
        List<Document> similarDocs = vectorStore.similaritySearch(afterPurified, topk, thresold);

        String knowledgeContext = "";
        if (similarDocs != null && similarDocs.isEmpty()) {
            log.info("vectorSearch 未查询到相关数据，尝试使用通用知识回答");
        } else {
            log.info("vector查询完毕");

            if (similarDocs != null) {
                log.info(">>> RAG 检索到的上下文 ({}条):", similarDocs.size());

                List<Map<String, Object>> bm25 = BM25Search(afterPurified,50);

                List<String> vectorIds = similarDocs.stream().map(Document::getId).toList();
                List<String> bm25Ids = bm25.stream().map(m -> m.get("id").toString()).toList();
                List<String> rrfIds = rrfCombine(vectorIds,bm25Ids);

                List<Document> finalSimilarDocs = similarDocs;
                similarDocs = rrfIds.stream().limit(15).map(id -> finalSimilarDocs.stream().filter(doc -> doc.getId().equals(id)).findFirst().orElse(null)).filter(Objects::nonNull).toList();

                log.info("RRF评分后");
                similarDocs.forEach(docu -> log.info(convertDocumentForPrint(docu)));

                similarDocs = rerank.rerank(afterPurified,similarDocs);
                knowledgeContext = similarDocs.parallelStream().map(Document::getText).collect(Collectors.joining("\n"));

                log.info("重排序后");
                similarDocs.forEach(docu -> log.info(convertDocumentForPrint(docu)));
            }
        }
        return knowledgeContext;
    }

    /**
     * 根据用户输入内容，查询向量库
     * @param afterPurified 待查询内容
     * @param topk 查询参数 TOP-K
     * @param thresold 相似度 thresold+distance=1
     * @param filter metadata中type过滤内容
     * */
    public String vectorSearch(String afterPurified,int topk,double thresold,String filter){
        List<Document> similarDocs = vectorStore.similaritySearch(afterPurified, topk, thresold,filter);

        String knowledgeContext = "";
        if (similarDocs != null && similarDocs.isEmpty()) {
            log.info("vectorSearch 未查询到相关数据，尝试使用通用知识回答");
        } else {
            log.info("vector查询完毕");

            if (similarDocs != null) {
                log.info(">>> RAG 检索到的上下文 ({}条):", similarDocs.size());
                similarDocs.forEach(docu -> log.info(convertDocumentForPrint(docu)));
                knowledgeContext = similarDocs.parallelStream().map(Document::getText).collect(Collectors.joining("\n"));
            }
        }
        return knowledgeContext;
    }

    private String filterThinkAnswer(String rawResponse){
        // 使用正则表达式：(?s) 表示让 . 匹配包括换行符在内的所有字符
        // <think>.*?</think> 匹配从开始标签到结束标签的所有内容
        return rawResponse.replaceAll("(?s)^.*?</think>", "").trim();
    }

    private String convertDocumentForPrint(Document document){
        return ">>> 元数据 %s  ID %s".formatted(json.parseObject(document.getMetadata()),document.getId());
    }

    private List<Map<String, Object>> BM25Search(String requirement,int size){
        String sql = """
                SELECT id, content,
                       MATCH(content) AGAINST('%s' IN NATURAL LANGUAGE MODE) AS score
                FROM knowledge_chunks
                WHERE MATCH(content) AGAINST('%s' IN NATURAL LANGUAGE MODE)
                ORDER BY score DESC
                LIMIT %d
                """.formatted(requirement,requirement,size);
        return jdbcTemplate.queryForList(sql);
    }

    public List<String> rrfCombine(List<String> vectorIds, List<String> mysqlIds) {
        Map<String, Double> scoreMap = new HashMap<>();
        int k = 60; // 经典的 RRF 常数

        // 处理向量路排名
        for (int i = 0; i < vectorIds.size(); i++) {
            String id = vectorIds.get(i);
            scoreMap.put(id, scoreMap.getOrDefault(id, 0.0) + 1.0 / (k + i + 1));
        }

        // 处理 MySQL BM25 路排名
        for (int i = 0; i < mysqlIds.size(); i++) {
            String id = mysqlIds.get(i);
            // 如果两边都有这个 ID，分数会累加，排名自然靠前
            scoreMap.put(id, scoreMap.getOrDefault(id, 0.0) + 1.0 / (k + i + 1));
        }

        // 按最终得分倒序排列
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
