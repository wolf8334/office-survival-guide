package com.xhr.springai.officeSurvivalGuide.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AIClient {

    private final WebClient.Builder webClient;
    private final ObjectMapper objectMapper;

    public AIResponse chat(AIRequest req) {
        Map<String, Object> body = buildBody(req);

        String raw = webClient.build().post()
                .uri(req.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + req.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("错误响应体: {}", errorBody);
                                    return Mono.error(new RuntimeException(errorBody));
                                })
                )
                .bodyToMono(String.class)
                .block();

        return parseResponse(raw);
    }

    public Flux<String> chatStream(AIRequest req) {
        Map<String, Object> body = buildBody(req);
        body.put("stream", true);

        return webClient.build().post()
                .uri(req.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + req.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(sse -> sse.data() != null && !sse.data().equals("[DONE]"))
                .mapNotNull(sse -> {
                    try {
                        JsonNode node = objectMapper.readTree(sse.data());
                        JsonNode delta = node.path("choices").path(0).path("delta");

                        String reasoning = delta.path("reasoning_content").asText("");
                        String content = delta.path("content").asText("");

                        if (!reasoning.isEmpty()) {
                            return "\u0001" + reasoning.replaceAll("\\n{2,}", "\n\n");  // 用不可见字符标记是 reasoning
                        }
                        if (!content.isEmpty()) {
                            return content.replaceAll("\\n{3,}", "\n\n");
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    private Map<String, Object> buildBody(AIRequest req) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(req.getSystem())) {
            messages.add(Map.of("role", "system", "content", req.getSystem()));
        }

        if (StringUtils.hasText(req.getImageBase64())) {
            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:" + req.getMimeType() + ";base64," + req.getImageBase64())
            ));
            content.add(Map.of("type", "text", "text", req.getUser()));
            messages.add(Map.of("role", "user", "content", content));
        } else {
            messages.add(Map.of("role", "user", "content", req.getUser()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", req.getModel());
        body.put("messages", messages);
        body.put("max_tokens", req.getMaxTokens() != null ? req.getMaxTokens() : 50000);

        // stop
        if (req.getStop() != null && !req.getStop().isEmpty()) {
            body.put("stop", req.getStop());
        }

        // 额外参数，比如 temperature、top_p、reasoning_effort 等
        if (req.getExtraParams() != null) {
            body.putAll(req.getExtraParams());
        }

        return body;
    }

    private AIResponse parseResponse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choice = root.path("choices").path(0);

            String content = choice.path("message").path("content").asText();
            String finishReason = choice.path("finish_reason").asText();

            JsonNode usage = root.path("usage");
            long promptTokens = usage.path("prompt_tokens").asLong();
            long completionTokens = usage.path("completion_tokens").asLong();


            if (content.contains("</think>")) {
                log.info("原始响应 {}", content);
                content = content.replaceAll("(?s).*?</think>", "").trim();
            }

            log.debug("大模型响应 {}", content);

            if (!"stop".equalsIgnoreCase(finishReason)) {
                log.info("finish_reason: {} ", finishReason);
            }
            log.info("promptTokens {} completionTokens {} totalTokens {}", promptTokens, completionTokens, promptTokens + completionTokens);

            return AIResponse.builder()
                    .content(content)
                    .finishReason(finishReason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("解析响应失败: " + raw, e);
        }
    }
}
