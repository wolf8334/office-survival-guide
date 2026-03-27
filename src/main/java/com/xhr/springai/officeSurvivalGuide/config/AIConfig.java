package com.xhr.springai.officeSurvivalGuide.config;

import com.xhr.springai.officeSurvivalGuide.advisor.TokenAdvisor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class AIConfig {

    private static final Logger log = LoggerFactory.getLogger(AIConfig.class);

    @Value("${custom.coder-name}")
    private String coderName;

    @Value("${custom.rerank-name}")
    private String rerankName;

    @Value("${custom.qwen-name}")
    private String qwenName;

    @Bean("qwenClient")
    @Primary
    public ChatClient chatClientBuilder(@NonNull ChatClient.Builder builder, TokenAdvisor tokenAdvisor) {
        log.info("加载通用生成器专家模型 {}", qwenName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(qwenName).stop(List.of("```", "```json")).build();

        return builder.clone()
                .defaultSystem("你是一位通用知识专家，协助用户完成工作。")
                .defaultAdvisors(tokenAdvisor)
                .defaultOptions(options).build();
    }

    @Bean("coderClient")
    public ChatClient dsChatClient(@NonNull ChatClient.Builder builder, TokenAdvisor tokenAdvisor) {
        log.info("加载代码生成模型 {}", coderName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(coderName).stop(List.of("```", "```json")).build();

        return builder.clone()
                .defaultSystem("你是一位技术专家，不要输出Markdown格式，不要解释。")
                .defaultAdvisors(tokenAdvisor)
                .defaultOptions(options).build();
    }

    @Bean("rerankClient")
    public ChatClient reRankChatClient(@NonNull ChatClient.Builder builder, TokenAdvisor tokenAdvisor) {
        log.info("加载重排序专家模型 {}", rerankName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(rerankName).stop(List.of("```", "```json")).build();

        return builder.clone()
                .defaultSystem("你是一位优秀的重排序专家，协助用户完成重排序工作。")
                .defaultAdvisors(tokenAdvisor)
                .defaultOptions(options).build();
    }
}
