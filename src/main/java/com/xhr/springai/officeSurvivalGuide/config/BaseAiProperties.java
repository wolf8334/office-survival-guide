package com.xhr.springai.officeSurvivalGuide.config;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BaseAiProperties {
    private String name;
    private String url;
    private String apiKey;
    private List<String> stop;
    private Map<String, Object> param;
}
