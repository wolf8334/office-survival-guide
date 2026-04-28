package com.xhr.springai.officeSurvivalGuide.client;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AIRequest {

    private String baseUrl;
    private String apiKey;
    private String model;
    private String system;
    private String user;
    private Integer maxTokens;
    private List<String> stop;
    private Map<String, Object> extraParams;
    private String imageBase64;
    private String mimeType;

}
