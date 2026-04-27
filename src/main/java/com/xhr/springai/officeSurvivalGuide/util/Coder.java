package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class Coder implements ICaller {

    private final ChatClient chatClient;

    public Coder(@Qualifier("coderClient") ChatClient client) {
        this.chatClient = client;
    }

    public String call(String expansionPrompt,String requirement){
        if (requirement == null || requirement.isBlank()){
            return this.call(expansionPrompt,"请开始");
        }
        ChatResponse response = chatClient.prompt()
                .system(expansionPrompt)
                .user(requirement)
                .call()
                .chatResponse();

        String content = null;

        String finishReason = null;

        if (response != null) {
            finishReason = response.getResult().getMetadata().getFinishReason();
            content = response.getResult().getOutput().getText();

//            log.info("response {}",response);
//            log.info("Output {}",response.getResult().getOutput());
//            log.info("metadata {}",response.getResult().getMetadata());
        }
        if (!"stop".equalsIgnoreCase(finishReason)) {
            log.warn("停止原因：{}", finishReason);
        }

//        return chatClient.prompt().system(expansionPrompt).user(requirement).call().content();
        return content;
    }

    public String call(String expansionPrompt){
        return chatClient.prompt().user(expansionPrompt).call().content();
    }

    public Flux<String> callFlux(String vectorResult, String afterPurified) {
        return chatClient.prompt().user(u -> u.text(" 背景知识：{context} 用户问题：{query}").param("context", vectorResult).param("query", afterPurified)).stream().content();
    }
}
