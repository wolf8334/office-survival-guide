package com.xhr.springai.officeSurvivalGuide.util;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoderUtil {

    private static final Logger log = LoggerFactory.getLogger(CoderUtil.class);

    private final Coder coder;
    private final VectorStoreUtil vectorStore;
    private final EmbeddingModel embeddingModel;

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
        String translated = filterThinkAnswer(coder.call(expansionPrompt, requirement));

        log.info("大模型回答 {}", translated);

        return translated;
    }

    /**
     * 流式调用AI智能体
     * @param requirement 用户原始输入
     * @param purification 提示词，用于AI智能体整理用户输入
     * @param expansionPrompt 提示词，用于向量查询后给智能体的提示词
     * @param topk topk
     * @param thresold 相似度，0到1
     * */
    public Flux<String> callWithPurificationAndKnowledgeStream(String requirement, String purification, String expansionPrompt, int topk, double thresold) {
        String afterPurified = purifiy(requirement);
        log.info("流式响应 afterPurified {}", afterPurified);

        String vectorResult = vectorSearch(afterPurified,topk,thresold);
        log.debug("coder流式响应 vectorResult {}", vectorResult);

        return coder.callFlux(vectorResult, expansionPrompt + " " + afterPurified);
    }

    public String callUserStatement(String requirement) {
        if (requirement.isBlank()) {
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        String translated = filterThinkAnswer(coder.call(requirement));
        log.info("大模型回答 {}", translated);

        return translated;
    }

    public String vectorString(String requirement) {
        float[] vector = embeddingModel.embed(requirement);
        return "[" + Arrays.toString(vector).replace("[", "").replace("]", "") + "]";
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
        String afterPurified = requirement.length() > 21 ? coder.call(purification, requirement) : requirement;
        log.info("用户抽象问题 '{}'，提取为：{}", requirement, afterPurified);

        return afterPurified;
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
                similarDocs.forEach(doc -> log.info(">>> {}", doc.getFormattedContent()));
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
}
