package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LLMUtil {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final Chater chater;
    private final VectorStoreUtil vectorStore;
    private final EmbeddingModel embeddingModel;

    /***
     * 调用LLM，包含对用户输入的提炼，包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> callWithPurificationAndKnowledge(String requirement,String expansionPrompt){
        if (requirement.isBlank()){
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 提炼用户输入 转化成标准查询词
        String purification = """
                你是一个专业的搜索引擎查询优化器。用户的输入可能是口语化的、包含情绪的或者是模糊的。 你的任务是：
                
                1 识别用户的核心搜索意图。
                2 将其转化为 2-3 个最精准的实体名词或属性词，用于数据库检索。
                3 严禁输出整句，严禁包含“我想”、“请问”等主观词汇。
                4 多个关键词用空格分隔。
                
                示例： 用户：哎呀累死了，年假能休多久啊？ 输出：年假 天数 规定。
                """;
        String afterPurified = chater.call(purification,requirement);
        log.info("用户抽象问题 '{}'，提取为：{}", requirement, afterPurified);
        float[] vector = embeddingModel.embed(afterPurified);
        log.info(Arrays.toString(vector));

        // 2 查询向量库，搜索相关内容
        List<Document> similarDocs = vectorStore.similaritySearch(afterPurified,10);

        String knowledgeContext = "";
        if (similarDocs != null && similarDocs.isEmpty()) {
            log.info("未查询到相关数据，尝试使用通用知识回答");
        } else {
            log.info("vector查询完毕");

            if (similarDocs != null) {
                log.info(">>> RAG 检索到的上下文 ({}条):", similarDocs.size());
                similarDocs.forEach(doc -> log.info(">>> {}", doc.getFormattedContent()));
                knowledgeContext = similarDocs.parallelStream().map(Document::getText).collect(Collectors.joining("\n"));
            }
        }

        expansionPrompt = expansionPrompt.formatted(knowledgeContext);

        String translated = chater.call(expansionPrompt, afterPurified);
        log.info("用户输入已扩展 {}", translated);
        return Result.success(requirement,translated);
    }

    /***
     * 调用LLM，包含对用户输入的提炼，不包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> callWithPurificationWithoutKnowledge(String requirement,String expansionPrompt){
        if (requirement.isBlank()){
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 提炼用户输入 转化成标准查询词
        String purification = """
                你是一个用户输入净化器。用户输入的文字里可能包含脏话，清晰化用词，也可能包含一些typo错误的地方。
                请把这些内容过滤一下，把用户的输入整理成适合llm以及vector查询的内容。
                """;
        String afterPurified = chater.call(purification,requirement);
        log.info("用户抽象问题 '{}'，提取为：{}", requirement, afterPurified);

        // 2 分析用户输入
        String translated = chater.call(expansionPrompt, afterPurified);

        log.info("用户输入已扩展 {}", translated);

        return Result.success(requirement,translated);
    }

    /***
     * 调用LLM，不包含对用户输入的提炼，不包含向量库信息
     * @param requirement 用户输入
     * @param expansionPrompt 提示词
     * */
    public Result<CommonData> call(String requirement,String expansionPrompt){
        if (requirement.isBlank()){
            requirement = "用户啥也没说，你替他说两句好听的。";
        }

        // 1 分析用户输入
        String translated = chater.call(expansionPrompt, requirement);

        log.info("用户输入已扩展 {}", translated);

        return Result.success(requirement,translated);
    }
}
