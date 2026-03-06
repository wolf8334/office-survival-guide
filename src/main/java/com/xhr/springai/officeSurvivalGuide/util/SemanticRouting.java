package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.config.Semantic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供意图分拣/语义路由功能
 * */
@Service
@RequiredArgsConstructor
public class SemanticRouting {

    private final LLMUtil llm;

    /**
     * 分拣用户输入的问题，拆分成系统支持的功能
     * */
    public Semantic routeSemantic(String requirment) {
        String prompt = """
                你是一个任务分发器。请分析用户的问题，并从以下几个标签中选择一个返回。
                
                标签：
               - KNOWLEDGE: 涉及政策、规定、面积标准、制度描述等文本类问题，需要基于知识库分析的。
               - SQL: 涉及统计、求和、查询具体数值、销售报表、明细数据等数据类问题，可以通过数据库查询解决的。
                
               规则：
                - 只输出标签名（KNOWLEDGE 或 DATA等），不要任何解释。
                """;
        Result<CommonData> rs = llm.call(requirment, prompt);
        String senmatic = rs.data().translate();
        if (senmatic.trim().equals("KNOWLEDGE")) {
            return Semantic.KNOWLEDGE;
        }  else if (senmatic.trim().equals("SQL")) {
            return Semantic.SQL;
        } else {
            return Semantic.OTHER;
        }
    }
}
