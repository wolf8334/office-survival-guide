package com.xhr.springai.officeSurvivalGuide.config;

import com.xhr.springai.officeSurvivalGuide.advisor.TokenAdvisor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class AIConfig {

    private static final Logger log = LoggerFactory.getLogger(AIConfig.class);

    @Value("${custom.coder-name}")
    private String coderName;

    @Value("${custom.embedding-name}")
    private String embeddingName;

    @Value("${custom.qwen-name}")
    private String qwenName;

    @Value("${custom.vl-name}")
    private String vlName;

    @Value("${spring.ai.anthropic.chat.options.model}")
    private String claudeName;

    @Value("${custom.coder-tokens}")
    private long coderToken;

    @Value("${custom.embedding-tokens}")
    private long embeddingToken;

    @Value("${custom.qwen-tokens}")
    private long qwenToken;

    @Value("${custom.vl-tokens}")
    private long vlToken;

    @Value("${custom.maxMessage}")
    private int maxMessage;

    private final JdbcTemplate jdbcTemplate;

    @Bean("qwenClient")
    @Primary
    public ChatClient chatClientBuilder(@Qualifier("openAiChatModel") ChatModel chatModel, TokenAdvisor tokenAdvisor) {
        log.info("加载通用生成器专家模型 {}", qwenName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(qwenName).build();
        if (!qwenName.toLowerCase().contains("gpt")){
            //GPT不支持stop
            options =  OpenAiChatOptions.builder().model(qwenName).stop(List.of("```", "```json")).build();
        }

        return ChatClient.builder(chatModel).clone()
                .defaultSystem("你是一位通用知识专家，协助用户完成工作。")
                .defaultAdvisors(tokenAdvisor)
//                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                .defaultOptions(options).build();
    }

    @Bean("coderClient")
    public ChatClient coderClient(@Qualifier("openAiChatModel") ChatModel chatModel, TokenAdvisor tokenAdvisor) {
        log.info("加载代码生成模型 {}", coderName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(coderName).build();
        if (!coderName.toLowerCase().contains("gpt")){
            //GPT不支持stop
            options =  OpenAiChatOptions.builder().model(coderName).stop(List.of("```", "```json")).build();
        }

        return ChatClient.builder(chatModel).clone()
                .defaultSystem("你是一位技术专家，不要输出Markdown格式，不要解释。")
                .defaultAdvisors(tokenAdvisor)
//                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                .defaultOptions(options).build();
    }

    @Bean("rerankClient")
    public ChatClient reRankChatClient(@Qualifier("openAiChatModel") ChatModel chatModel, TokenAdvisor tokenAdvisor) {
        log.info("加载向量化专家模型 {}", embeddingName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(embeddingName).build();
        if (!embeddingName.toLowerCase().contains("gpt")){
            //GPT不支持stop
            options =  OpenAiChatOptions.builder().model(embeddingName).stop(List.of("```", "```json")).build();
        }

        return ChatClient.builder(chatModel).clone()
                .defaultSystem("你是一位优秀的重排序专家，协助用户完成重排序工作。")
                .defaultAdvisors(tokenAdvisor)
//                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                .defaultOptions(options).build();
    }

    @Bean("vlClient")
    public ChatClient vlClient(@Qualifier("openAiChatModel") ChatModel chatModel, TokenAdvisor tokenAdvisor) {
        log.info("加载识别专家模型 {}", vlName);
        OpenAiChatOptions options = OpenAiChatOptions.builder().model(vlName).build();
        if (!vlName.toLowerCase().contains("gpt")){
            //GPT不支持stop
            options =  OpenAiChatOptions.builder().model(vlName).stop(List.of("```", "```json")).build();
        }

        return ChatClient.builder(chatModel).clone()
                .defaultSystem("你是一位优秀的图片识别专家，协助用户完成图片识别工作。")
                .defaultAdvisors(tokenAdvisor)
                .defaultOptions(options).build();
    }

    @Bean("claudeClient")
    public ChatClient claudeClient(@Qualifier("anthropicChatModel") ChatModel chatModel, TokenAdvisor tokenAdvisor) {
        log.info("加载Claude专家模型 {}", claudeName);
        AnthropicChatOptions options = AnthropicChatOptions.builder().model(claudeName).build();
        if (!claudeName.toLowerCase().contains("gpt")){
            //GPT不支持stop
            options =  AnthropicChatOptions.builder().model(claudeName).stopSequences(List.of("```", "```json")).build();
        }

        return ChatClient.builder(chatModel).clone()
                .defaultSystem("你是一位优秀的图片识别专家，协助用户完成图片识别工作。")
                .defaultAdvisors(tokenAdvisor)
                .defaultOptions(options).build();
    }

    @Bean("chatMemoryRepository")
    public ChatMemoryRepository chatMemoryRepo(){
        log.info("加载对话持久层");
        return JdbcChatMemoryRepository.builder().jdbcTemplate(jdbcTemplate)
//                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();
    }

    @Bean("chatMemory")
    public ChatMemory chatMemory(){
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepo()).maxMessages(maxMessage).build();
    }
}
