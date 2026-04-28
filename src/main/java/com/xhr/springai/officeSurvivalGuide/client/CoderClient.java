package com.xhr.springai.officeSurvivalGuide.client;

import com.xhr.springai.officeSurvivalGuide.config.CoderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component("httpCoderClient")
@RequiredArgsConstructor
public class CoderClient {

    private final AIClient client;
    private final CoderProperties properties;

    public AIResponse call(String systemMessage, String userMessage) {

        AIRequest request = AIRequest.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey())
                .model(properties.getName())
                .system(systemMessage)
                .user(userMessage)
                .stop(properties.getStop())
                .extraParams(properties.getParam())
                .build();

        log.info("code model {}", properties.getName());

        return client.chat(request);
    }

    public AIResponse call(String userMessage) {

        AIRequest request = AIRequest.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey())
                .model(properties.getName())
                .user(userMessage)
                .stop(properties.getStop())
                .extraParams(properties.getParam())
                .build();

        return client.chat(request);
    }

    public Flux<String> callFlux(String systemMessage, String userMessage) {

        AIRequest request = AIRequest.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey())
                .model(properties.getName())
                .user(userMessage)
                .stop(properties.getStop())
                .extraParams(properties.getParam())
                .build();

        return client.chatStream(request);
    }

    public Flux<String> callFlux(String userMessage) {

        AIRequest request = AIRequest.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey())
                .model(properties.getName())
                .user(userMessage)
                .stop(properties.getStop())
                .extraParams(properties.getParam())
                .build();

        return client.chatStream(request);
    }

}
