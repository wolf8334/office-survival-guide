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

    public List<Map<String,String>> parseKeywords(String rawJson){
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
            List<Map<String,String>> list = objectMapper.readValue(processedJson, new TypeReference<List<Map<String, String>>>() {});

//            var dupList = list.stream()
//                    .map(m -> m.get("keyword"))
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
//                    .entrySet().stream()
//                    .filter(e -> e.getValue() > 1).toList();
//
//            if (!dupList.isEmpty()){
//                log.info("返回值有重复数据 {}",processedJson);
//                dupList.forEach(e -> log.info("重复关键词: {}, 重复次数: {}", e.getKey(), e.getValue()));
//            }

            return list;
        } catch (Exception e) {
            // 报错时打印原始内容，方便根据日志修 Prompt
            log.error(e.getMessage());
            log.error("解析失败，LLM吐出的脏数据: " + rawJson);
            return Collections.emptyList();
        }
    }

    public String parseString(Object obj){
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
