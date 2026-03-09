package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.Chater;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
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

    @Qualifier("tidbDataSource")
    private final DataSource ds2;


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

    public List<Map<String,Object>> list(){
        String sql = "SELECT * FROM sys_expert_rules order by id";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 从sys_expert_rule中获取keyword为空的数据，从大模型获取关键字并填充
     * */
    public void acquireKeyword(){
        String sql = "select id,left(explanation,2000) as explanation from sys_expert_rules where category is null";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);

        log.info("查询到{}条待更新知识库",list.size());

        int batchSize = 1;

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
                    请提取以下每段内容的依据国民经济行业分类标准的对应的门类代码，返回内容仅包含门类代码，不包含代码名称。
                    严格按 JSON 格式返回数组：[{"id": "ID_数字", "keyword": "门类代码"}, ...]
                    格式约束：仅返回标准 JSON，严禁任何解释性文字。
                    在返回 JSON 前，请检查提取的门类代码是否与原文描述的业务领域相符。如果不符，请重新提取。
                    不允许输出 ```json 这种格式的，直接返回json内容
                    
                    游戏和娱乐新闻都是娱乐业
                    
                    门类代码	门类代码名称
                    A	    农、林、牧、渔业
                    B	    采矿业
                    C	    制造业
                    D	    电力、燃气及水的生产和供应业
                    E	    建筑业
                    F	    交通运输、仓储和邮政业
                    G	    信息传输、计算机服务和软件业
                    H	    批发和零售业
                    I	    住宿和餐饮业
                    J	    金融业
                    K	    房地产业
                    L	    租赁和商务服务业
                    M	    科学研究、技术服务和地质勘查业
                    N	    水利、环境和公共设施管理业
                    O	    居民服务和其他服务业
                    P	    教育
                    Q	    卫生、社会保障和社会福利业
                    R	    文化、体育和娱乐业
                    S	    公共管理和社会组织
                    T	    国际组织
                    
                    内容如下：
                    """ + promptInfo;

                        // 4. 调用 Spring AI (硅基流动)
                        String jsonResponse = chater.call(prompt);
                        log.info("大模型调用完成");

                        // 5. 解析结果
                        List<Map<String, String>> results = json.parseKeywords(jsonResponse);

                        // 6. 批量更新数据库 (Batch Update)
                        jdbcTemplate.batchUpdate(
                                "UPDATE sys_expert_rules SET category = ?,created_at = now()::timestamp AT TIME ZONE 'UTC+8' WHERE id = ?",
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

        String content = String.format("【业务规则】关键词：%s。详细定义：%s", keyword, explanation);

        Document doc = new Document(content, Map.of("type", "RULE"));
        vectorStore.add(List.of(doc));
        log.info("专家知识库添加完毕 " + knowledgeBase);
    }

    @Scheduled(fixedDelay = 60000 * 10)
    private void refreshVectorStore() throws SQLException{
        // 查询未向量化的数据
        String sql = "select id,keyword,explanation from sys_expert_rules where id not in (SELECT (metadata ->> 'id')::int FROM vector_store where (metadata ->> 'id') is not null) and keyword is not null and keyword != ''";
        String vector = "delete from vector_store where content in (select keyword from public.sys_expert_rules where keyword is not null group by keyword having count(1) > 1)";
        String expertRule = "UPDATE sys_expert_rules a set KEYWORD = null WHERE KEYWORD IN (SELECT keyword FROM sys_expert_rules WHERE keyword IS NOT NULL GROUP BY keyword HAVING count(1) > 1)";

        String refreshDBInfo = "delete from vector_store where content like '表名%'";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        List<Document> documents = new ArrayList<>();

        if (!list.isEmpty()) {
            documents = new ArrayList<>(list.stream().filter(row -> row.get("keyword") != null && !row.get("keyword").toString().isBlank()).map(row -> {
                String keyword = (String) row.get("keyword");
                Integer id = (Integer) row.get("id");
                String explanation = (String) row.get("explanation");

                String combinedContent = String.format("主题：%s。详细内容：%s", keyword, explanation);

                return new Document(combinedContent, Map.of("keyword", keyword, "raw_explanation", explanation,"id",id));
            }).toList());
        }

        documents.addAll(indexTables());

        // 2 刷新数据库表结构信息到向量库
        long start = System.currentTimeMillis();

        //清除向量库中的表结构信息
        jdbcTemplate.execute(refreshDBInfo);

        //更新知识库中未向量化的内容
        vectorStore.add(documents);

        //删除重复数据
        jdbcTemplate.execute(vector);
        jdbcTemplate.execute(expertRule);

        log.info("向量库刷新成功。共处理字段: {} 条, Embedding 耗时: {} ms", documents.size(), (System.currentTimeMillis() - start));
    }

    private List<Document> indexTables() throws SQLException {
        List<Document> list = new ArrayList<>();

        Connection conn = ds2.getConnection();
        DatabaseMetaData metaData = conn.getMetaData();
        String currentDb = conn.getCatalog();

        // 1. 获取所有表
        ResultSet tables = metaData.getTables(currentDb, null, "%", new String[]{"TABLE"});

        while (tables.next()) {
            String tableName = tables.getString("TABLE_NAME");
            String tableRemarks = tables.getString("REMARKS"); // 获取表注释

            if (tableRemarks.isBlank()) continue;

            // 2. 获取该表的所有字段和注释
            ResultSet columns = metaData.getColumns(null, null, tableName, "%");
            StringBuilder columnInfo = new StringBuilder();
            while (columns.next()) {
                columnInfo.append(columns.getString("COLUMN_NAME"));

                if (!columns.getString("REMARKS").isBlank()){
                    //字段注释
                    columnInfo.append("(").append(columns.getString("REMARKS")).append(") ");
                }

                columnInfo.append(",");
            }

            // 3. 获取外键关系（关联关系自动提取）
            ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);
            StringBuilder fkInfo = new StringBuilder();
            while (foreignKeys.next()) {
                fkInfo.append(tableName).append("通过")
                        .append(foreignKeys.getString("FKCOLUMN_NAME"))
                        .append("关联").append(foreignKeys.getString("PKTABLE_NAME")).append("; ");
            }

            String tableIdentity = String.format(
                    "表名：%s；含义：%s；字段：%s；关联关系：%s",
                    tableName, tableRemarks, columnInfo, fkInfo
            );

            log.debug(tableIdentity);

            list.add(new Document(tableIdentity, Map.of("ddl", getTableDDL(tableName,conn))));
        }

        log.info("刷新了{}条数据库信息", list.size());

        return list;
    }

    private String getTableDDL(String tableName, Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        StringBuilder ddl = new StringBuilder("CREATE TABLE " + tableName + " (");

        // 1. 获取所有列信息
        try (ResultSet columns = metaData.getColumns(null, null, tableName, "%")) {
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String typeName = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                String remarks = columns.getString("REMARKS"); // 这是最重要的：字段注释

                ddl.append("  ").append(columnName).append(" ").append(typeName);
                if (columnSize > 0 && !typeName.contains("text") && !typeName.contains("int")) {
                    ddl.append("(").append(columnSize).append(")");
                }

                // 把注释直接作为 SQL 注释写在后面，AI 看得最明白
                if (remarks != null && !remarks.isEmpty()) {
                    ddl.append(" -- ").append(remarks);
                }
                ddl.append(",");
            }
        }

        // 2. 获取主键信息（可选，有助于 AI 理解唯一性）
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName)) {
            while (primaryKeys.next()) {
                ddl.append("  PRIMARY KEY (").append(primaryKeys.getString("COLUMN_NAME")).append("),\n");
            }
        }

        // 去掉最后一个逗号并闭合
        if (ddl.charAt(ddl.length() - 2) == ',') {
            ddl.deleteCharAt(ddl.length() - 2);
        }
        ddl.append(");");

        return ddl.toString();
    }
}

