package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.systemInterface.ICaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class Chater implements ICaller {

    private static final Logger log = LoggerFactory.getLogger(Chater.class);

    private final ChatClient chatClient;

    public Chater(@Qualifier("qwenClient") ChatClient client) {
        this.chatClient = client;
    }

    public String call(String expansionPrompt,String requirement){
        if (requirement == null || requirement.isBlank()){
            return this.call(expansionPrompt,"请开始");
        }
        return chatClient.prompt().system(expansionPrompt).user(requirement).call().content();
    }

    public String call(String expansionPrompt){
        // 1. 获取完整的 Response 对象
        ChatResponse response = chatClient.prompt().user(expansionPrompt).call().chatResponse(); // 注意这里换成 chatResponse()

        // 2. 从 Metadata 中提取 Usage
        Usage usage = response.getMetadata().getUsage();

        if (usage != null) {
            Integer promptTokens = usage.getPromptTokens();      // 输入的 Token
            Integer completionTokens = usage.getCompletionTokens(); // 模型输出的 Token
            Integer totalTokens = usage.getTotalTokens();           // 总计

            log.info("本次请求消耗Token -> 输入: {}, 输出: {}, 总计: {}",
                    promptTokens, completionTokens, totalTokens);
        }

        // 3. 拿到你原来的 JSON 字符串继续解析
        return response.getResult().getOutput().getText();
    }
}
