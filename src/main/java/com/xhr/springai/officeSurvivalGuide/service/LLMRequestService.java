package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.bean.LLMRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMRequestService {

    private final JdbcTemplate jdbcTemplate;
    private static final int MAX_LENGTH = 1000;
    private String localIp;

    @PostConstruct
    void resolveLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        localIp = addr.getHostAddress();
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        localIp = "0.0.0.0";
    }

    public Long save(LLMRequest req) {
        if (req.getCreatedAt() == null) {
            req.setCreatedAt(LocalDateTime.now());
        }

        var content = truncate(req.getPrompt());
        req.setPrompt(content.content());
        req.setPromptTruncated(content.truncated());

        if (req.getIp() == null || req.getIp().isBlank()) {
            req.setIp(localIp);
        }
        if (req.getUserId() == null) {
            req.setUserId(1L);
        }

        String sql = """
                INSERT INTO llm_request
                (created_at, model_name, endpoint, input_tokens,
                 prompt, prompt_truncated, success, user_id, ip)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql,
                Timestamp.valueOf(req.getCreatedAt()),
                req.getModelName(),
                req.getEndpoint(),
                req.getInputTokens(),
                req.getPrompt(),
                req.getPromptTruncated(),
                req.getSuccess() != null ? req.getSuccess() : true,
                req.getUserId(),
                req.getIp());

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id;
    }

    @Async
    public void updateResponse(Long id, String response, Integer inputTokens, Integer outputTokens,
                                Integer totalTokens, String finishReason, Integer statusCode,
                                String errorMessage, Boolean success, Integer durationMs) {
        var content = truncate(response);

        String sql = """
                UPDATE llm_request SET
                    response = ?, response_truncated = ?, input_tokens = ?, output_tokens = ?, total_tokens = ?,
                    finish_reason = ?, status_code = ?, error_message = ?,
                    success = ?, duration_ms = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(sql,
                content.content(),
                content.truncated(),
                inputTokens,
                outputTokens,
                totalTokens,
                finishReason,
                statusCode,
                errorMessage,
                success,
                durationMs,
                id);

        log.info("LLM 请求日志已更新, id={}, finish_reason={}, tokens={}", id, finishReason, totalTokens);
    }

    private record TruncatedContent(String content, boolean truncated) {}

    private TruncatedContent truncate(String text) {
        if (text == null || text.isEmpty()) {
            return new TruncatedContent(text, false);
        }
        if (text.length() > MAX_LENGTH) {
            return new TruncatedContent(text.substring(0, MAX_LENGTH), true);
        }
        return new TruncatedContent(text, false);
    }

    public List<Map<String, Object>> list(int page, int size) {
        String sql = """
                SELECT id, created_at, duration_ms, model_name, endpoint,
                       input_tokens, output_tokens, total_tokens,
                       prompt, prompt_truncated, response, response_truncated,
                       finish_reason, status_code, error_message, success, ip
                FROM llm_request
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.queryForList(sql, size, (page - 1) * size);
    }

    public long count() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_request", Long.class);
        return result != null ? result : 0;
    }
}
