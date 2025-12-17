package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.RerankResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RerankParser {

    private static final Logger log = LoggerFactory.getLogger(RerankParser.class);


    /**
     * 解析 LLM 返回的多行字符串
     * @param llmOutput LLM 返回的原始文本 (包含换行)
     * @return 解析后的对象列表
     */
    public static List<RerankResult> parse(String llmOutput) {
        List<RerankResult> results = new ArrayList<>();

        if (llmOutput == null || llmOutput.isBlank()) {
            return results;
        }

        // 1. 按行切割
        String[] lines = llmOutput.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                // 2. 核心逻辑：只按第一个空格切分，分成两部分
                // limit=2 保证内容里的空格不会被切断
                String[] parts = line.split("\\s+", 2);

                if (parts.length == 2) {
                    // 第一部分转 int
                    int score = Integer.parseInt(parts[0]);
                    // 第二部分是内容
                    String content = parts[1];

                    results.add(new RerankResult(score, content));
                } else {
                    // 只有分数没有内容，或者格式不对，根据情况处理
                    log.error("格式跳过: " + line);
                }
            } catch (NumberFormatException e) {
                // 防止 LLM 偶尔发疯输出不是数字的东西
                log.error("分数解析失败: " + line);
            }
        }
        return results;
    }
}