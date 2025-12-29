package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.Chater;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VectorStoreUtil vectorStore;
    private final Chater chater;
    private final JSONUtil json;


    public void refreshKnowledgeBase() {
        log.info("开始执行专家知识库全量刷新任务");

        String selectSql = "SELECT distinct keyword,explanation FROM public.sys_expert_rules";
        List<Map<String, Object>> sourceData = jdbcTemplate.queryForList(selectSql);

        if (sourceData.isEmpty()) {
            log.warn("源表为空，跳过刷新");
            return;
        }

        try {
            jdbcTemplate.execute("TRUNCATE TABLE vector_store");
            log.info("专家知识库数据已清空");

            List<Document> documents = sourceData.stream().map(row -> {
                String keyword = (String) row.get("keyword");
                String explanation = (String) row.get("explanation");

                String fullContent = String.format("%s:%s", keyword, explanation);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("category", "业务规则"); // metadata 只放这种用来做硬过滤的短标签
                metadata.put("tag",keyword);
                metadata.put("content",explanation);

                // 第一个参数 fullContent 是向量计算的内容
                return new Document(fullContent, metadata);
            }).toList();

            long start = System.currentTimeMillis();
            vectorStore.add(documents);
            long end = System.currentTimeMillis();

            log.info("专家知识库刷新成功。共处理字段: {} 条, Embedding 耗时: {} ms", documents.size(), (end - start));

        } catch (Exception e) {
            log.error("专家知识库刷新失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 从sys_expert_rule中获取keyword为空的数据，从大模型获取关键字并填充
     * */
    public void acquireKeyword(){
        String sql = "select id,left(explanation,2000) as explanation from sys_expert_rules where keyword is null";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        log.info("查询到{}条待更新知识库",list.size());

        int batchSize = 2;

        try (var executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < list.size(); i += batchSize) {
                int end = Math.min(i + batchSize, list.size());
                List<Map<String, Object>> batch = list.subList(i, end);

                // 开启虚拟线程
                executor.submit(() -> {
                    try {
                        log.info("任务开始，发送关键字{}条，剩余{}条",batchSize,list.size() - end);

                        long t = System.currentTimeMillis();

                        // 构造包含 ID 的提示词 格式如：ID_1: 内容... \n ID_2: 内容...
                        String promptInfo = batch.stream()
                                .filter(r -> !r.getOrDefault("explanation","").toString().isBlank())
                                .map(r -> "ID_" + r.getOrDefault("id","") + " " + r.getOrDefault("explanation","") )
                                .collect(Collectors.joining("\n"));

                        String prompt = """
                    请提取以下每段内容的关键词。
                    严格按 JSON 格式返回数组：[{"id": "ID_数字", "keyword": "关键词1, 关键词2"}, ...]
                    唯一性检查：输出前必须检查 keywords 列表，严禁出现重复项。
                    格式约束：仅返回标准 JSON，严禁任何解释性文字。
                    返回内容不要笼统，要能体现实际词条内容，不要只输出条目词，要体现原有词条含义。
                    在返回 JSON 前，请检查提取的关键词是否与原文描述的业务领域相符。如果不符，请重新提取。
                    不允许输出 ```json 这种格式的，直接返回json内容
                    内容如下：
                    """ + promptInfo;

                        // 4. 调用 Spring AI (硅基流动)
                        String jsonResponse = chater.call(prompt);
                        log.info("大模型调用完成");

                        // 5. 解析结果
                        List<Map<String, String>> results = json.parseKeywords(jsonResponse);

                        // 6. 批量更新数据库 (Batch Update)
                        jdbcTemplate.batchUpdate(
                                "UPDATE sys_expert_rules SET keyword = ?,created_at = now()::timestamp AT TIME ZONE 'UTC+8' WHERE id = ?",
                                new BatchPreparedStatementSetter() {
                                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                                        var item = results.get(i);
                                        ps.setString(1, item.get("keyword"));
                                        ps.setLong(2, Long.parseLong(item.get("id").replace("ID_", "")));
                                    }
                                    public int getBatchSize() { return results.size(); }
                                }
                        );

                        log.info("完成调用,耗时 {} 毫秒",System.currentTimeMillis() - t);
                    } catch (Exception e) {
                        log.error("批量提交失败 {}", e.getMessage());
                        if (e.getMessage().startsWith("429")){
                            try {
                                log.error("当前线程休眠1分钟");
                                TimeUnit.MINUTES.sleep(1);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } finally {
                    }
                });
            }
        }
    }

    public void addKnowledgeBase(Map<String, String> knowledgeBase) {
        String keyword = knowledgeBase.get("keyword");
        String explanation = knowledgeBase.get("explanation");

        String sql = "INSERT into sys_expert_rules (keyword,explanation) values ('" + keyword + "','" + explanation + "')";
        jdbcTemplate.execute(sql);

        // 把这条知识灌入向量库,加上特殊的 Metadata，标记这是“专家规则”
        // 格式：【专家业务规则】关键词：设备状态，逻辑含义：设备状态是指DEVICE表的STATUS
        String content = String.format("【业务规则】关键词：%s。详细定义：%s", keyword, explanation);

        Document doc = new Document(content, Map.of("type", "RULE"));
        vectorStore.add(List.of(doc));
        log.info("专家知识库添加完毕 " + knowledgeBase);
    }

    @Scheduled(fixedDelay = 60000)
    private void refreshVectorStore(){
        // 查询未向量化的数据
        String sql = "select id,keyword from sys_expert_rules where id not in (SELECT (metadata ->> 'id')::int FROM vector_store) and keyword is not null and keyword != ''";
        String vector = "delete from vector_store where content in (select keyword from public.sys_expert_rules where keyword is not null group by keyword having count(1) > 1)";
        String expertRule = "UPDATE sys_expert_rules a set KEYWORD = null WHERE KEYWORD IN (SELECT keyword FROM sys_expert_rules WHERE keyword IS NOT NULL GROUP BY keyword HAVING count(1) > 1)";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        if (!list.isEmpty()) {
            List<Document> documents = list.stream().filter(row -> row.get("keyword") != null && !row.get("keyword").toString().isBlank()).map(row -> {
                String keyword = (String) row.get("keyword");
                Integer id = (Integer) row.get("id");

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("keyword", keyword);
                metadata.put("id", id);

                return new Document(keyword, metadata);
            }).toList();

            long start = System.currentTimeMillis();
            vectorStore.add(documents);

            jdbcTemplate.execute(vector);
            jdbcTemplate.execute(expertRule);

            log.info("向量库刷新成功。共处理字段: {} 条, Embedding 耗时: {} ms", documents.size(), (System.currentTimeMillis() - start));
        }
    }
}

