package com.xhr.springai.officeSurvivalGuide.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class JSONUtil {

    private static final Logger log = LoggerFactory.getLogger(JSONUtil.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, String>> parseKeywords(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 清理 Markdown 代码块标签
            String processedJson = rawJson.trim();
            if (processedJson.contains("```")) {
                // 兼容 ```json 和 ``` 两种情况
                processedJson = processedJson.replaceAll("(?s)```(json)?(.*?)```", "$2").trim();
            }

            // 校验返回的json的keyword之间是否有重复，如有，输出原始json
            List<Map<String, String>> list = objectMapper.readValue(processedJson, new TypeReference<List<Map<String, String>>>() {
            });

            return list;
        } catch (Exception e) {
            // 报错时打印原始内容，方便根据日志修 Prompt
            log.error(e.getMessage());
            log.error("解析失败，LLM吐出的脏数据: " + rawJson);
            return Collections.emptyList();
        }
    }

    public String parseObject(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public <T> T parseString(String json,Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
    public <T> T parseString(String json,TypeReference<T> tr) {
        try {
            return objectMapper.readValue(json, tr);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 使用Jackson处理所有需要转义的字符
     * @param json 待处理的json串
     * @return 处理后的字符串
     * */
    public String parseComma(String json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public String cleanContent(String content) {
        if (content == null) return "";
        // 1. 将多个连续的换行替换为单个换行
        String step1 = content.replaceAll("(\\r\\n|\\n|\\r){2,}", "\n");
        // 2. 将连续的空格替换为一个空格
        String step2 = step1.replaceAll(" +", " ");
        // 3. 去掉首尾空格
        return step2.trim();
    }
}
