package com.xhr.springai.officeSurvivalGuide.bean;

public record RerankResult(int score, String content) {

    @Override
    public String toString() {
        return "分数: " + score + ", 内容: " + content;
    }
}
