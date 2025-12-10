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
public class SQLExpertService {

    private static final Logger log = LoggerFactory.getLogger(SQLExpertService.class);

    private final LLMUtil llm;

    public Result<CommonData> writeSomeSQL(String requirement) {

        if (null == requirement || requirement.isBlank()) {
            return Result.userNoInput();
        }

        //调用LLM 让他把表达美化一下
        String expansionPrompt = """
                你是一位SQL专家，擅长编写复杂SQL，请使用PG方言编写符合用户要求的语句。不要废话，直接编写就行， 输出语句不要包含换行，不要包括```sql这种内容。
                
                已知的表结构如下：
                create table sys_expert_rules
                (
                    id          serial primary key,
                    keyword     varchar(2000),
                    explanation text,
                    created_at  timestamp default now()
                );
                """;

        return llm.call(requirement,expansionPrompt);
    }
}
