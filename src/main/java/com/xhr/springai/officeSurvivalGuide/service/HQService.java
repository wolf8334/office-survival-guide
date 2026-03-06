package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.config.Semantic;
import com.xhr.springai.officeSurvivalGuide.util.LLMUtil;
import com.xhr.springai.officeSurvivalGuide.util.SemanticRouting;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class HQService {

    private static final Logger log = LoggerFactory.getLogger(HQService.class);

    private final LLMUtil llm;
    private final SemanticRouting route;

    public Flux<String> acknowledge(String requirement) {
        //结合后勤知识库，回答问题。
        Semantic semantic = route.routeSemantic(requirement);
        if (semantic == Semantic.KNOWLEDGE) {
            log.info("用户提问知识类问题");
            String purification = "请将用户输入的问题进行整理，符合正式的对话要求，去掉语气词。例如用户问，处长能住多大房子啊，你需要翻译成，处长的住房面积最大是多少。";
            String systemInstructions = """
                    你是一个电力公司后勤系统的知识库问答助手。
                    请参考以下背景信息来回答用户的问题。如果背景信息中没有相关内容，请礼貌地告知你不知道。
                    """;

            return llm.callWithPurificationAndKnowledgeStream(requirement, purification, systemInstructions, 10, 0.5);
        } else if (semantic == Semantic.SQL) {
            log.info("用户提问数据库类问题");
        } else {
            log.info("未知分类问题 {}",requirement);
        }
        return null;
    }
}
