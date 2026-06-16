package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.LLMRequest;
import com.xhr.springai.officeSurvivalGuide.mcp.tools.WeatherTools;
import com.xhr.springai.officeSurvivalGuide.service.LLMRequestService;
import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class Chater implements ICaller {

    private static final Logger log = LoggerFactory.getLogger(Chater.class);

    @Qualifier("toolClient")
    private final ChatClient chatClient;
    private final String conversationId = "chater";
    private final String fluxConversationId = "chater_flux";
    private final LLMRequestService logService;
    private final WeatherTools weatherTools;

    @Value("${custom.chat.name}")
    private String chatName;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    public String call(String expansionPrompt, String requirement) {
        long start = System.currentTimeMillis();

        String prompt = "[system] " + expansionPrompt + " [user] " + requirement;
        LLMRequest logReq = new LLMRequest();
        logReq.setModelName(chatName);
        logReq.setEndpoint(baseUrl + "/chat/completions");
        logReq.setPrompt(prompt);
        logReq.setSuccess(true);
        Long logId = logService.save(logReq);

        try {
            ChatResponse chatResponse = chatClient.prompt().system(expansionPrompt).user(requirement).tools(weatherTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)).call().chatResponse();

            String ret = null;
            if (chatResponse != null) {
                ret = Objects.requireNonNull(chatResponse.getResult()).getOutput().getText();
            }

            int durationMs = (int) (System.currentTimeMillis() - start);

            if (chatResponse != null) {
                Usage usage = chatResponse.getMetadata().getUsage();

                Integer inputTokens = usage.getPromptTokens();
                Integer outputTokens = usage.getCompletionTokens();
                Integer totalTokens = usage.getTotalTokens();
                logService.updateResponse(logId, ret, inputTokens, outputTokens, totalTokens,
                        "stop", 200, null, true, durationMs);
            }

            return ret;
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - start);
            logService.updateResponse(logId, null, null, null, null,
                    "error", 500, e.getMessage(), false, durationMs);
            throw e;
        }
    }

    public String call(String requirement) {
        long start = System.currentTimeMillis();

        LLMRequest logReq = new LLMRequest();
        logReq.setModelName(chatName);
        logReq.setEndpoint(baseUrl + "/chat/completions");
        logReq.setPrompt(requirement);
        logReq.setSuccess(true);
        Long logId = logService.save(logReq);

        try {
            ChatResponse chatResponse = chatClient.prompt().user(requirement).tools(weatherTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)).call().chatResponse();
            String ret = null;
            if (chatResponse != null) {
                ret = Objects.requireNonNull(chatResponse.getResult()).getOutput().getText();
            }

            int durationMs = (int) (System.currentTimeMillis() - start);

            if (chatResponse != null) {
                Usage usage = chatResponse.getMetadata().getUsage();

                Integer inputTokens = usage.getPromptTokens();
                Integer outputTokens = usage.getCompletionTokens();
                Integer totalTokens = usage.getTotalTokens();
                logService.updateResponse(logId, ret, inputTokens, outputTokens, totalTokens,
                        "stop", 200, null, true, durationMs);
            }

            return ret;
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - start);
            logService.updateResponse(logId, null, null, null, null,
                    "error", 500, e.getMessage(), false, durationMs);
            throw e;
        }
    }

    public Flux<String> callFlux(String vectorResult, String afterPurified) {
        return chatClient.prompt().user(u -> u.text(" 背景知识：{context} 用户问题：{query}").param("context", vectorResult).param("query", afterPurified))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, fluxConversationId)).stream().chatClientResponse().doOnNext(chunk -> {
                    ChatGenerationMetadata metadata = null;
                    if (chunk.chatResponse() != null) {
                        metadata = chunk.chatResponse().getResult().getMetadata();
                        // 获取停止原因
                        String stopReason = metadata.getFinishReason();
                        if (stopReason != null && !stopReason.isEmpty() && !stopReason.equalsIgnoreCase("STOP")) {
                            log.info("流停止原因: " + stopReason);
                        }
                    }
                }).map(chunk ->{
                    String content = null;
                    if (chunk.chatResponse() != null) {
                        content = chunk.chatResponse().getResult().getOutput().getText();
                    }
                    return content != null ? content : "";
                });
    }
}
