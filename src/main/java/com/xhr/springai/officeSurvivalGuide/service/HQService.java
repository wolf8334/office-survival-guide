package com.xhr.springai.officeSurvivalGuide.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xhr.springai.officeSurvivalGuide.config.Semantic;
import com.xhr.springai.officeSurvivalGuide.util.DBUtil;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.LLMUtil;
import com.xhr.springai.officeSurvivalGuide.util.SemanticRouting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HQService {

    private final LLMUtil llm;
    private final JSONUtil json;
    private final DBUtil dbUtil;

    @Qualifier("tidbJdbcTemplate")
    private final JdbcTemplate mysql;
    private final SemanticRouting route;

    public Flux<String> acknowledge(String requirement) throws JsonProcessingException {
        //结合后勤知识库，回答问题。
        Semantic semantic = route.routeSemantic(requirement);
        if (semantic == Semantic.KNOWLEDGE) {
            log.info("用户提问知识类问题");
            return knowledge(requirement);
        } else if (semantic == Semantic.SQL) {
            log.info("用户提问数据库类问题");
            return writeSQL(requirement).flatMap(content -> {
                log.info("content: {}", content);

                if ("NO_TABLE".equals(content)) {
                    return knowledge(requirement);
                } else {
                    return Flux.just(content);
                }
            });
        } else {
            log.info("未知分类问题 {}", requirement);
            return knowledge(requirement);
        }
    }

    public Flux<String> knowledge(String requirement) {
        String purification = "请将用户输入的问题进行整理，符合正式的对话要求，去掉语气词。例如用户问，处长能住多大房子啊，你需要翻译成，处长的住房面积最大是多少。";
        String systemInstructions = """
                你是一个电力公司后勤系统的知识库问答助手。
                请参考以下背景信息来回答用户的问题。如果背景信息中没有相关内容，请礼貌地告知你不知道。
                """;

        return llm.callWithPurificationAndKnowledgeStream(requirement, purification, systemInstructions, 10, 0.5);
    }

    public Flux<String> writeSQL(String requirement) throws JsonProcessingException {
        Flux<String> NO_TABLE = Flux.just("NO_TABLE");

        if (null == requirement || requirement.isBlank()) {
            return NO_TABLE;
        }

        //查询是否有相关表
        String vectorResult = llm.vectorSearch(requirement, 10, 0.4);
        log.info("vectorString: {}", llm.vectorString(requirement));
        log.debug("writeSQL vectorResult {}", vectorResult);

        if (!vectorResult.isEmpty()) {
           String sqlPrompt = """
                    你是一个MySQL专家，根据相关表结构和用户需求写SQL，不需要输出你思考的过程，而是直接给出最终的基于MySQL方言，输出你认为可行的表名，如果有多张表，使用英文逗号分隔。
                    表定义是
                    %s
                    """.formatted(vectorResult);
            String tableName = llm.callForString(requirement,sqlPrompt);
            log.info("tableName: {}", tableName);

            if (tableName != null) {

                String ddl = dbUtil.getDDL(tableName);
                log.debug("ddl: {}", ddl);

                List<Map<String, Object>> orgData = mysql.queryForList("select org_no,org_name from org_org");
                String orgJSON = json.parseString(orgData);

                String dataSamples = dbUtil.getDatas(tableName);
                log.debug("dataSamples: {}", dataSamples);

                String sqlPrompt2 = """
                        请根据用户要求
                        %s，
                        结合表%s的示例数据
                        %s，
                        表%s的结构是
                        %s，
                        编写查询语句，不允许输出查询语句以外的任何语句，也就是不允许输出UPDATE、INSERT、DELETE。
                        输出语句中，要核对字段是否正确，只能使用存在的表和字段，也要考虑功能是否正确。生成完成后，需要检查语句是否可以正确执行。
                        系统常用的单位名称和单位编码的对应关系是%s。
                        """.formatted(requirement, tableName, dataSamples, tableName, ddl, orgJSON);

                String sql = llm.callUserStatement(sqlPrompt2);

                try {
                    List<Map<String, Object>> result = mysql.queryForList(sql);
                    String resultJSON = json.parseString(result);
                    log.info("resultJSON: {}", resultJSON);

                    String sqlPrompt3 = """
                        请根据用户要求
                        %s，
                        结合示例数据
                        %s，
                        结合表结构
                        %s，
                        组织给用户的回复。
                        """.formatted(requirement, resultJSON, ddl);
                    return Flux.just(llm.callUserStatement(sqlPrompt3));
                } catch (Exception e) {
                    e.printStackTrace();
                    String msg = e.getMessage();
                    log.info("msg: {}", msg);
                    //writeSQL(requirement + " 你刚才生成可执行SQL的时候报错了，语句是 " + sql + " 错误信息是 " + msg);
                }
            }
        }

        return NO_TABLE;
    }
}
