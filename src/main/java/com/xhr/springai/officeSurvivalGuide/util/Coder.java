package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class Coder implements ICaller {

    private final ChatClient chatClient;

    public Coder(@Qualifier("coderClient") ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String call(String expansionPrompt,String requirement){
        if (requirement == null || requirement.isBlank()){
            return this.call(expansionPrompt,"请开始");
        }
        return chatClient.prompt().system(expansionPrompt).user(requirement).call().content();
    }

    public String call(String expansionPrompt){
        return chatClient.prompt().system(expansionPrompt).user("请开始").call().content();
    }
}
