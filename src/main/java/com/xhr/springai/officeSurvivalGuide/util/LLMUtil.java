package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.RerankResult;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LLMUtil {

    private static final Logger log = LoggerFactory.getLogger(LLMUtil.class);

    private final Chater chater;
    private final VectorStoreUtil vectorStore;
    private final EmbeddingModel embeddingModel;
    private final ChatMemory memory;

    private final String conversationId = "chater";
    private final String fluxConversationId = "chater_flux";


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
     * 调用LLM，包含对用户输入的提炼，包含向量库信息
     * @param requirement 用户输入
     * */
    public Result<CommonData> callWithPurificationAndKnowledgeRerank(String requirement) {
        // 1 提纯
        String afterPurified = purifiy(requirement);

        // 2 查询向量库，搜索相关内容
        String knowledgeContext = vectorSearch(afterPurified, 10, 0.46);

        String rerank = """
                你是一个知识提纯和关联度专家 我给你一堆向量搜索的结果和用户原始提问 你告诉我每条向量搜索结果和用户原始提问的相关度
                回答格式是分数在前面 空格分割 后面是向量搜索的结果 分数从0到100 整数 没有分数
                
                用户原始问题
                %s
                
                向量库搜索结果
                %s
                """.formatted(requirement, knowledgeContext);
        String rerankResult = chater.call(rerank);

        List<RerankResult> list = RerankParser.parse(rerankResult).stream().peek(rrr -> log.info(rrr.toString())).filter(rrr -> rrr.score() >= 50).toList();

        if (list.isEmpty()) {
            return Result.success(requirement, "无相关结果，未找到符合条件的业务规则");
        } else {
            String rerankPrompt = """
                    你现在是一个严格的【专家规则库】回复助手。
                    
                    【已知知识库内容】：
                    %s
                    
                    【回复准则】：
                    1. **语义理解**：请理解业务含义。例如：“不耗电” = “不用电”；“物理阻隔” = “非电子”。
                    2. **排除法**：如果用户要求“不用电”，请排除任何提及“需电力”、“电源”、“传感器”的选项。
                    3. **输出格式**：找到后，直接提取该规则的内容，不要废话。
                    4. 若实在没有任何相关内容（哪怕是沾边的），才回答“未找到符合条件的业务规则”。
                    """;
            String knowledges = list.stream().map(RerankResult::content).collect(Collectors.joining("\n"));

            rerankPrompt = rerankPrompt.formatted(knowledges);
            log.info("knowledges {}", knowledges);
            log.info("requirement {}", requirement);

            String translated = chater.call(rerankPrompt, requirement);
            log.info("Rerank 大模型回答 {}", translated);

            return Result.success(requirement, translated);
        }
    }

    /***
     * 调用LLM，包含对用户输入的提炼，不包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> callWithPurificationWithoutKnowledge(String requirement, String expansionPrompt) {
        // 1 提炼用户输入 转化成标准查询词
        String afterPurified = purifiy(requirement);

        // 2 分析用户输入
        String translated = chater.call(expansionPrompt, afterPurified);

        log.info("WithoutKnowledge 大模型回答 {}", translated);

        return Result.success(requirement, translated);
    }

    /***
     * 调用LLM，不包含对用户输入的提炼，不包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> call(String requirement, String expansionPrompt) {
        if (requirement.isBlank()) {
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 分析用户输入
        String translated = filterThinkAnswer(chater.call(expansionPrompt, requirement));

        log.info("大模型回答 {}", translated);

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
                similarDocs.forEach(doc -> log.info(">>> {}", doc.getFormattedContent()));
                knowledgeContext = similarDocs.parallelStream().map(Document::getText).collect(Collectors.joining("\n"));
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
