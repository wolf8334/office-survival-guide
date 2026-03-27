package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class Chater implements ICaller {

    private static final Logger log = LoggerFactory.getLogger(Chater.class);

    private final ChatClient chatClient;

    public Chater(@Qualifier("qwenClient") ChatClient client) {
        this.chatClient = client;
    }

    public String call(String expansionPrompt, String requirement) {
        return chatClient.prompt().system(expansionPrompt).user(requirement).call().content();
    }

    public String call(String requirement) {
        return chatClient.prompt().user(requirement).call().content();
    }

    public Flux<String> callFlux(String vectorResult, String afterPurified) {
        return chatClient.prompt().user(u -> u.text(" 背景知识：{context} 用户问题：{query}").param("context", vectorResult).param("query", afterPurified)).stream().content();
    }
}
