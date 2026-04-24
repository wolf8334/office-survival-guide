package com.xhr.springai.officeSurvivalGuide.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class GenerateResult {
    private List<Map<String, String>> files;
    private String downloadUrl;
    private String token;
}
