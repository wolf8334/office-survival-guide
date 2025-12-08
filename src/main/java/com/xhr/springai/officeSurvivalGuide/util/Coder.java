package com.xhr.springai.officeSurvivalGuide.util;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class Coder {

    private final ChatClient chatClient;

    public Coder(@Qualifier("coderClient") ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String call(String expansionPrompt,String requirement){
        return chatClient.prompt().system(expansionPrompt).user(requirement).call().content();
    }
}
