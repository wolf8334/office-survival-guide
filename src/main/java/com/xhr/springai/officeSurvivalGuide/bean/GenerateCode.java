package com.xhr.springai.officeSurvivalGuide.bean;

import lombok.Data;

import java.util.List;

@Data
public class GenerateCode {

    private String prompt;
    private String analysis;
    private List<FileItem> files;
    private String model;
    private String techRequirements;
}
