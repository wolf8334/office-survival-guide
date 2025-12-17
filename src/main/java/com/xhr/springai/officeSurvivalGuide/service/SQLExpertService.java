package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.util.LLMUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SQLExpertService {

    private static final Logger log = LoggerFactory.getLogger(SQLExpertService.class);

    private final LLMUtil llm;

    @Qualifier("tidbJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    public Result<CommonData> writeSomeSQL(String requirement) {

        if (null == requirement || requirement.isBlank()) {
            return Result.userNoInput();
        }

        String expansionPrompt = """
                # Role
                        你是一个【表名提取器】
                
                        # Core Mission (核心任务)
                        你的唯一任务是：从用户需求中分析出需要用到哪些表，并列出表名。
                
                        # Negative Constraints (绝对禁止)
                        1. **严禁编写SQL语句**：不要输出 `SELECT`, `FROM`, `JOIN` 等任何SQL代码。
                        2. **严禁废话**：不要解释为什么选这些表。
                
                        # Output Format (输出格式)
                        仅输出纯文本的表名，一行一个。
                
                        # Workflow
                        1. 阅读【已知表结构】。
                        2. 理解【用户需求】。
                        3. 在心里构建SQL（不要输出来！），确定用到了哪些表。
                        4. 仅输出这些表的英文名。
                        5. 回答之前，看看在不在给你的表清单里
                        6. 输出用到的所有表
                
                        # Task Start
                        【已知表清单】
                """;

        String tableInfoSQL = "SELECT concat('表名: ',table_name,',表内数据含义: ',table_comment) as str FROM information_schema.TABLES where table_schema = 'sakila'";

        List<String> tableInfo = jdbcTemplate.queryForList(tableInfoSQL,String.class);
        String tables = String.join("\n",tableInfo);

        expansionPrompt += tables;

        log.info("调用大模型，分析用到的表");
        return llm.call(requirement,expansionPrompt);
    }
}
