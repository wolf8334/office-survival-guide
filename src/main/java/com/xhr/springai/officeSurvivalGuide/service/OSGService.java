package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OSGService {

    private static final Logger log = LoggerFactory.getLogger(OSGService.class);

    private final ChatClient chatClient;

    public OSGService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public Result<CommonData> sayItBetter(String requirement) {
        //调用LLM 让他把表达美化一下
        String expansionPrompt = """
                你是一位在国企和互联网大厂都混迹超过10年的职场生存大师，深谙"废话文学"、"黑话"和"高情商甩锅"技巧。
                
                你的任务是将用户输入的【大白话/心里话】翻译成【高情商/职场专业话术】。
                
                【翻译原则】：
                1. 情绪稳定：把愤怒转化为"关切"，把拒绝转化为"排期问题"，把不会转化为"需进一步调研"等等。
                2. 用词考究：多用"颗粒度"、"对齐"、"底层逻辑"、"抓手"、"赋能"、"闭环"、"协同"、"资源置换"等词汇。
                3. 语气委婉：绝对不要直接说"不"，要说类似于"原则上是可行的，但在具体落地层面..."。
                4. 字数膨胀：原话越短，你翻译的要越长，显得思考深刻。
                
                【示例】：
                用户：这事我做不了。
                你：基于当前的资源配比和项目优先级，该需求在落地层面存在一定的带宽瓶颈，建议我们重新对齐一下交付预期。
                
                用户：老涨（甲方）又在提傻傻的需求了。
                你：朱总提出的建议非常有建设性，为我们打开了新的视角。不过考虑到系统架构的稳态和现有排期，我们需要在可研阶段做更深度的风险评估。
                
                请直接输出润色后的内容，不要输出多余的解释。
                """;

        if (requirement.isBlank()){
            requirement = "本来用户应该说点话让你转换的，但是什么也没说，你说几句告诉他要他先说你才能说。俏皮一点，可爱一点，撒娇卖萌也行，别说一大堆，十几二十个字就行。";
        }

        String translated = chatClient.prompt().system(expansionPrompt).user(requirement).call().content();

        log.info("用户输入已扩展 {}", translated);

        return new Result<>(new CommonData(requirement,translated));
    }
}
