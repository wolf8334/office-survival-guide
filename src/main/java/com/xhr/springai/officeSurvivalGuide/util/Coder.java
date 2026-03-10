package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
        return chatClient.prompt().system(expansionPrompt).user(requirement).call().content();
    }

    public String call(String expansionPrompt){
        return chatClient.prompt().user(expansionPrompt).call().content();
    }

    public Flux<String> callFlux(String vectorResult, String afterPurified) {
        return chatClient.prompt().user(u -> u.text(" 背景知识：{context} 用户问题：{query}").param("context", vectorResult).param("query", afterPurified)).stream().content();
    }
}
