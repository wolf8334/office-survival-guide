package com.xhr.springai.officeSurvivalGuide.client;

import com.xhr.springai.officeSurvivalGuide.bean.LLMRequest;
import com.xhr.springai.officeSurvivalGuide.service.LLMRequestService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class AIClient {

    private final WebClient.Builder webClient;
    private final JsonMapper objectMapper;
    private final LLMRequestService logService;

    public AIResponse chat(AIRequest req) {
        long start = System.currentTimeMillis();

        LLMRequest logReq = new LLMRequest();
        logReq.setModelName(req.getModel());
        logReq.setEndpoint(req.getBaseUrl() + "/chat/completions");
        logReq.setPrompt(buildPromptText(req));
        logReq.setSuccess(true);
        Long logId = logService.save(logReq);

        try {
            Map<String, Object> body = buildBody(req);
            log.debug("body {}",body);

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

            AIResponse aiResp = parseResponse(raw);
            int durationMs = (int) (System.currentTimeMillis() - start);

            logService.updateResponse(logId, aiResp.getContent(),
                    (int) aiResp.getPromptTokens(), (int) aiResp.getCompletionTokens(),
                    (int) aiResp.getTotalTokens(),
                    aiResp.getFinishReason(), 200, null, true, durationMs);

            return aiResp;
        } catch (Exception e) {
            int durationMs = (int) (System.currentTimeMillis() - start);
            logService.updateResponse(logId, null, null, null, null,
                    "error", 500, e.getMessage(), false, durationMs);
            throw e;
        }
    }

    public Flux<String> chatStream(AIRequest req) {
        long start = System.currentTimeMillis();

        LLMRequest logReq = new LLMRequest();
        logReq.setModelName(req.getModel());
        logReq.setEndpoint(req.getBaseUrl() + "/chat/completions");
        logReq.setPrompt(buildPromptText(req));
        logReq.setSuccess(true);
        Long logId = logService.save(logReq);

        Map<String, Object> body = buildBody(req);
        body.put("stream", true);

        StringBuilder fullResponse = new StringBuilder();
        AtomicReference<JsonNode> lastUsage = new AtomicReference<>();

        return webClient.build().post()
                .uri(req.getBaseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + req.getApiKey())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(sse -> sse.data() != null && !sse.data().equals("[DONE]"))
                .doOnNext(sse -> {
                    try {
                        JsonNode node = objectMapper.readTree(sse.data());
                        if (node.has("usage") && !node.get("usage").isNull()) {
                            lastUsage.set(node.get("usage"));
                        }
                    } catch (Exception ignored) {
                    }
                })
                .mapNotNull(sse -> {
                    try {
                        JsonNode node = objectMapper.readTree(sse.data());
                        JsonNode delta = node.path("choices").path(0).path("delta");

                        String reasoning = delta.path("reasoning_content").asString("");
                        String content = delta.path("content").asString("");

                        if (!reasoning.isEmpty()) {
                            fullResponse.append(reasoning);
                            return "\u0001" + reasoning.replaceAll("\\n{2,}", "\n\n");
                        }
                        if (!content.isEmpty()) {
                            fullResponse.append(content);
                            return content.replaceAll("\\n{3,}", "\n\n");
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .doOnTerminate(() -> {
                    int durationMs = (int) (System.currentTimeMillis() - start);
                    JsonNode usage = lastUsage.get();
                    Integer inputTokens = null;
                    Integer outputTokens = null;
                    Integer totalTokens = null;
                    if (usage != null) {
                        int pt = usage.path("prompt_tokens").asInt(-1);
                        int ct = usage.path("completion_tokens").asInt(-1);
                        int tt = usage.path("total_tokens").asInt(-1);
                        if (pt >= 0) inputTokens = pt;
                        if (ct >= 0) outputTokens = ct;
                        if (tt >= 0) totalTokens = tt;
                    }
                    logService.updateResponse(logId, fullResponse.toString(),
                            inputTokens, outputTokens, totalTokens,
                            "stream", 200, null, true, durationMs);
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
            log.info("parseResponse {}",raw);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choice = root.path("choices").path(0);

            String content = choice.path("message").path("content").asString();
            String finishReason = choice.path("finish_reason").asString();

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
            log.info("输入token {} 输出token {} 合计 {}", promptTokens, completionTokens, promptTokens + completionTokens);

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

    private String buildPromptText(AIRequest req) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(req.getSystem())) {
            sb.append("[system] ").append(req.getSystem());
            if (StringUtils.hasText(req.getUser())) {
                sb.append(" ");
            }
        }
        if (StringUtils.hasText(req.getUser())) {
            sb.append("[user] ").append(req.getUser());
        }
        if (StringUtils.hasText(req.getImageBase64())) {
            sb.append(" [image]");
        }
        return sb.toString();
    }
}
