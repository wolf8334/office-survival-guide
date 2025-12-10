package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.util.LLMUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OSGService {

    private static final Logger log = LoggerFactory.getLogger(OSGService.class);

    private final LLMUtil llm;

    public Result<CommonData> sayItBetter(String requirement) {
        String expansionPrompt = """
                你是一位在国企和互联网大厂都混迹超过10年的职场生存大师，深谙"废话文学"、"黑话"和"高情商甩锅"技巧。
                
                你的任务是将用户输入的【大白话/心里话】翻译成【高情商/职场专业话术】。
                
                【翻译原则】：
                1. 情绪稳定：把愤怒转化为"关切"，把拒绝转化为"排期问题"，把不会转化为"需进一步调研"等等。
                2. 用词考究：多用"颗粒度"、"对齐"、"底层逻辑"、"抓手"、"赋能"、"闭环"、"协同"、"资源置换"等词汇。
                3. 语气委婉：绝对不要直接说"不"，要说类似于"原则上是可行的，但在具体落地层面"。
                4. 字数膨胀：翻译结果不要太短，也不要太长，长短适中。
                
                【示例】：
                用户：这事我做不了。
                你：基于当前的资源配比和项目优先级，该需求在落地层面存在一定的带宽瓶颈，建议我们重新对齐一下交付预期。
                
                用户：甲方又在提傻傻的需求了。
                你：提出的建议非常有建设性，为我们打开了新的视角。不过考虑到系统架构的稳态和现有排期，我们需要在可研阶段做更深度的风险评估。
                
                请直接输出润色后的内容，不要输出多余的解释。
                
                【已知业务规则】：
                %s
                """;
        return llm.callWithPurificationAndKnowledge(requirement,expansionPrompt);
    }

    public Result<CommonData> acknowledge(String requirement) {
        String expansionPrompt = """
                用户咨询公司制度相关的问题，请你根据公司相关规章制度和国家通用的规定和要求回答。
                如果公司规定比国家的好，就采用公司的，否则以国家规定为准。
                
                【已知业务规则】：
                %s
                """;
        return llm.callWithPurificationAndKnowledge(requirement,expansionPrompt);
    }
}
