package com.xhr.springai.officeSurvivalGuide.client;

import com.xhr.springai.officeSurvivalGuide.config.VLProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component("httpVLClient")
@RequiredArgsConstructor
public class VLClient {

    private final AIClient client;
    private final VLProperties properties;

    public AIResponse call(String systemMessage, String userMessage,String base64Image) {
        return call(systemMessage,userMessage,base64Image,"image/jpeg");
    }

    public AIResponse call(String systemMessage, String userMessage,String base64Image, String mimeType) {

        AIRequest request = AIRequest.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey())
                .model(properties.getName())
                .system(systemMessage)
                .user(userMessage)
                .imageBase64(base64Image)
                .mimeType(mimeType)
                .extraParams(properties.getParam())
                .build();

        log.info("vl model {}", properties.getName());

        return client.chat(request);
    }

    public Flux<String> callFlux(String systemMessage, String userMessage,String base64Image) {
        return callFlux(systemMessage,userMessage,base64Image,"image/jpeg");
    }

    public Flux<String> callFlux(String systemMessage, String userMessage,String base64Image, String mimeType) {

        AIRequest request = AIRequest.builder()
                .baseUrl(properties.getUrl())
                .apiKey(properties.getApiKey())
                .model(properties.getName())
                .system(systemMessage)
                .user(userMessage)
                .imageBase64(base64Image)
                .mimeType(mimeType)
                .extraParams(properties.getParam())
                .build();

        return client.chatStream(request);
    }
}
