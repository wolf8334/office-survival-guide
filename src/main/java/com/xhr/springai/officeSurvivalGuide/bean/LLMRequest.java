package com.xhr.springai.officeSurvivalGuide.bean;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LLMRequest {
    private Long id;
    private LocalDateTime createdAt;
    private Integer durationMs;
    private String modelName;
    private String endpoint;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String prompt;
    private Boolean promptTruncated;
    private String response;
    private Boolean responseTruncated;
    private String finishReason;
    private Integer statusCode;
    private String errorMessage;
    private Boolean success;
    private Long userId;
    private String ip;
}
