package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.config.IntentClassification;
import com.xhr.springai.officeSurvivalGuide.config.enums.INTENT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * 工作流程管理，保存从接到用户请求到返回用户内容的过程
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressUtil {

    private final LLMUtil llm;
    private final JSONUtil json;
    private final DBUtil dbUtil;
    private final SemanticRouting route;

    @Qualifier("tidbJdbcTemplate")
    private final JdbcTemplate mysql;

    private final RedisUtil redis;

    public Flux<String> processMessage(String requirement) {
        //语义识别
        IntentClassification ic = route.routeSemantic(requirement);
        String key = DigestUtils.md5DigestAsHex(json.parseObject(ic).getBytes());

        INTENT it = INTENT.other;
        if (ic != null) {
            it = ic.getIntent();
            log.info("用户输入 {} 重写为 {}",requirement,ic.getQuery_rewrite());
            requirement = ic.getQuery_rewrite();
        }
        log.info("it {}", it);

        if (EnumSet.of(INTENT.reason_analysis, INTENT.progress_status, INTENT.solution_action, INTENT.definition_explain).contains(it)) {
            log.info("用户提问知识类问题");
            redis.setObject(key, ic);
            return knowledge(requirement);
        } else if (EnumSet.of(INTENT.count_stat).contains(it)) {
            log.info("用户提问数据库类问题");
            String finalRequirement = requirement;
            return writeSQL(requirement).flatMap(content -> {
                log.info("content: {}", content);

                if ("NO_TABLE".equals(content)) {
                    return knowledge(finalRequirement);
                } else {
                    return Flux.just(content);
                }
            });
        } else {
            log.info("未知分类问题 {}", requirement);
            redis.setObject(key, ic);
            return knowledge(requirement);
        }
    }

    private Flux<String> knowledge(String requirement) {
        String purification = "请将用户输入的问题进行整理，符合正式的对话要求，去掉语气词。例如用户问，处长能住多大房子啊，你需要翻译成，处长的住房面积最大是多少。";
        String systemInstructions = """
                你是一个知识库问答助手。
                请参考以下背景信息来回答用户的问题。如果背景信息中没有相关内容，请礼貌地告知你不知道。
                
                %s
                
                请先列出参考资料中明确提到的字段和说明。
                禁止推理：如果参考资料中没有提到某个参数的逻辑，请直接跳过，严禁根据字段名猜测其含义。
                如果资料不足以支持完整回答，请在回答末尾注明“部分内容缺失”。
                """.formatted(llm.getExpertKonwledge());
        log.info("knowledge vectorString: {}", llm.vectorString(requirement));
        return llm.callWithPurificationAndKnowledgeStream(requirement, purification, systemInstructions, 50, 0.5);
    }

    @NonNull
    private Flux<String> writeSQL(String requirement) {
        Flux<String> NO_TABLE = Flux.just("NO_TABLE");

        if (null == requirement || requirement.isBlank()) {
            return NO_TABLE;
        }

        if (RetrySynchronizationManager.getContext() != null && RetrySynchronizationManager.getContext().getRetryCount() > 0) {
            requirement += " 之前生成的句子有SQL语法问题，请仔细检查语法。";
        }

        log.info("requirement {}", requirement);

        //查询是否有相关表
        String vectorResult = llm.vectorSearch(requirement, 10, 0.5, "表定义");
        log.info("vectorString: {}", llm.vectorString(requirement));
        log.debug("writeSQL vectorResult {}", vectorResult);

        if (!vectorResult.isEmpty()) {
            String sqlPrompt = """
                    你是一个MySQL专家，根据相关表结构和用户需求写SQL，不需要输出你思考的过程，而是直接给出最终的基于MySQL方言，输出你认为可行的表名，如果有多张表，使用英文逗号分隔。
                    不要输出JSON格式，直接返回表名即可。
                    表定义是
                    %s
                    """.formatted(vectorResult);
            String tableName = llm.callForString(requirement, sqlPrompt);
            log.info("tableName: {}", tableName);

            if (tableName != null) {

                String ddl = dbUtil.getDDL(tableName);
                log.debug("ddl: {}", ddl);

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
                        """.formatted(requirement, tableName, dataSamples, tableName, ddl);

                String sql = llm.callUserStatement(sqlPrompt2);

                try {
                    List<Map<String, Object>> result = mysql.queryForList(sql);
                    String resultJSON = json.parseObject(result);
                    log.info("resultJSON: {}", resultJSON);

                    String sqlPrompt3 = """
                            请根据用户要求
                            %s，
                            结合示例数据
                            %s，
                            结合表结构
                            %s，
                            组织给用户的回复。
                            回答用户问题时，不要提及数据库的表结构和你的统计思路，根据用户问题回答即可，直接告诉用户答案。
                            """.formatted(requirement, resultJSON, ddl);
                    return Flux.just(llm.callUserStatement(sqlPrompt3));
                } catch (Exception e) {
                    e.printStackTrace();
                    String msg = e.getMessage();
                    log.info("msg: {}", msg);
                }
            }
        }

        return NO_TABLE;
    }
}
