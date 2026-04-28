package com.xhr.springai.officeSurvivalGuide.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIResponse {

    private String content;
    private String finishReason;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;
}
