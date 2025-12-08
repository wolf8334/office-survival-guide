package com.xhr.springai.officeSurvivalGuide.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIConfig {

    private static final Logger log = LoggerFactory.getLogger(AIConfig.class);


    @Value("${custom.coder-name}")
    private String coderName;

    @Bean("qwenClient")
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * 基于DeepSeek的模型 逻辑好 会思考 但是也慢
     */
    @Bean("coderClient")
    public ChatClient.Builder dsChatClient(ChatClient.Builder builder) {
        log.info("加载代码生成模型 {}", coderName);

        return builder
                .defaultSystem("你是一位PostgreSQL数据库专家，只输出SQL语句，不要输出Markdown格式，不要解释。")
                .defaultOptions(OpenAiChatOptions.builder().model(coderName).temperature(0.1).maxTokens(4096).build());
    }
}
