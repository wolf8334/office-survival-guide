package com.xhr.springai.officeSurvivalGuide.advisor;

import com.xhr.springai.officeSurvivalGuide.service.TokenBucketService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class TokenAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenAdvisor.class);
    private final TokenBucketService tokenBucket;


    @Override
    @NonNull
    public ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, @NonNull CallAdvisorChain callAdvisorChain) {
        tokenBucket.reserveTokens();

        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

        if (response.chatResponse() != null) {
            logToken(response.chatResponse().getMetadata().getUsage());
        }

        return response;
    }

    @Override
    @NonNull
    public Flux<ChatClientResponse> adviseStream(@NonNull ChatClientRequest chatClientRequest, @NonNull StreamAdvisorChain streamAdvisorChain) {
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        tokenBucket.reserveTokens();

        return streamAdvisorChain.nextStream(chatClientRequest).doOnNext(advisedResponse -> {
            if (advisedResponse.chatResponse() != null) {
                lastUsage.set(advisedResponse.chatResponse().getMetadata().getUsage());
            }
        }).doOnTerminate(() -> {
            logToken(lastUsage.get());
        });
    }

    @Override
    @NonNull
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private void logToken(@NonNull Usage usage){

        long estimated = tokenBucket.getReversedTokens();

        // 输入的 Token
        Integer promptTokens = usage.getPromptTokens();

        // 模型输出的 Token
        Integer completionTokens = usage.getCompletionTokens();

        // 总计
        Integer totalTokens = usage.getTotalTokens();

        // 获取实际消耗，修正差值
        tokenBucket.settle(estimated, totalTokens);

        log.info("本次请求消耗Token -> 输入: {}, 输出: {}, 总计: {}",promptTokens, completionTokens, totalTokens);
    }
}
