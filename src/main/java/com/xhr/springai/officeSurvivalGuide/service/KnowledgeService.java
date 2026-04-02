package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VectorStoreUtil vectorStore;
    private final JSONUtil json;

    @Qualifier("tidbDataSource")
    private final DataSource ds2;

    @Value("${spring.ai.vectorstore.pgvector.schema-name:public}")
    private String pgvectorSchema;

    @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}")
    private String pgvectorTableName;


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
                metadata.put("tag", keyword);
                metadata.put("content", explanation);

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

    public List<Map<String, Object>> list() {
        String sql = "SELECT * FROM sys_expert_rules order by id";
        return jdbcTemplate.queryForList(sql);
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
    private void refreshVectorStore() throws SQLException {
        String fullVectorTableName = pgvectorSchema + "." + pgvectorTableName;
        log.info("fullVectorTableName {}", fullVectorTableName);

        // 查询未向量化的数据
        String sql = "select id,keyword,explanation,category from sys_expert_rules where id not in (SELECT (metadata ->> 'id')::int FROM " + fullVectorTableName + " where (metadata ->> 'id') is not null) and keyword is not null and keyword != ''";
        String vector = "delete from " + fullVectorTableName + " where content in (select keyword from public.sys_expert_rules where keyword is not null group by keyword having count(1) > 1)";
        String expertRule = "UPDATE sys_expert_rules a set KEYWORD = null WHERE KEYWORD IN (SELECT keyword FROM sys_expert_rules WHERE keyword IS NOT NULL GROUP BY keyword HAVING count(1) > 1)";

        String refreshDBInfo = "delete from " + fullVectorTableName + " where content like '表名%'";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        List<Document> documents = new ArrayList<>();

        if (!list.isEmpty()) {
            documents = new ArrayList<>(list.stream().filter(row -> row.get("keyword") != null && !row.get("keyword").toString().isBlank()).map(row -> {
                String keyword = (String) row.get("keyword");
                Integer id = (Integer) row.get("id");
                String explanation = (String) row.get("explanation");
                String category = (String) row.get("category");

                String combinedContent = json.cleanContent(String.format("主题：%s。详细内容：%s", keyword, explanation));

                return new Document(combinedContent, Map.of("keyword", keyword, "raw_explanation", explanation, "id", id, "sub-type", category,"type","专家知识库"));
            }).toList());
        }

        documents.addAll(indexTables());

        // 2 刷新数据库表结构信息到向量库
        long start = System.currentTimeMillis();

        //清除向量库中的表结构信息
        jdbcTemplate.execute(refreshDBInfo);

        //清除库中已存在的type=表定义、字段定义、专家知识库的内容
        vectorStore.delete("type == '表定义'");
        vectorStore.delete("type == '字段定义'");
        vectorStore.delete("type == '专家知识库'");

        //更新知识库中未向量化的内容
        vectorStore.add(documents);

        //删除重复数据
        jdbcTemplate.execute(vector);
        jdbcTemplate.execute(expertRule);

        log.info("向量库刷新成功。共处理字段: {} 条, Embedding 耗时: {} ms", documents.size(), (System.currentTimeMillis() - start));
    }

    private List<Document> indexTables() throws SQLException {
        List<Document> list = new ArrayList<>();

        try (Connection conn = ds2.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String currentDb = conn.getCatalog();

            log.info("currentDb: {}", currentDb);

            // 1. 获取所有表
            ResultSet tables = metaData.getTables(currentDb, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableRemarks = tables.getString("REMARKS"); // 获取表注释

                if (tableRemarks.isBlank()) continue;

                log.debug("表名: {}  中文名: {}", tableName, tableRemarks);

                String tableIdentity = json.cleanContent(String.format(
                        "表名：%s。含义：%s。",
                        tableName, tableRemarks
                ));

                list.add(new Document(tableIdentity, Map.of("table_name", tableName, "tableRemarks", tableRemarks, "type", "表定义")));
                list.addAll(indexColumns(tableName));
            }
        }

        log.info("刷新了{}条数据库信息", list.size());

        return list;
    }

    private List<Document> indexColumns(String tableName) throws SQLException {
        List<Document> list = new ArrayList<>();

        try (Connection conn = ds2.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            log.debug("indexColumns: {}", tableName);

            // 获取字段信息
            ResultSet columns = metaData.getColumns(null, null, tableName, "%");

            StringBuilder columnInfo = new StringBuilder();
            while (columns.next()) {
                columnInfo.append(columns.getString("COLUMN_NAME"));

                if (!columns.getString("REMARKS").isBlank()) {
                    //字段注释
                    columnInfo.append("(").append(columns.getString("REMARKS")).append(") ");
                }
                columnInfo.append(",");
            }

            // 获取主外键关系
            ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName);
            StringBuilder fkInfo = new StringBuilder();
            while (foreignKeys.next()) {
                fkInfo.append(tableName).append("通过")
                        .append(foreignKeys.getString("FKCOLUMN_NAME"))
                        .append("关联").append(foreignKeys.getString("PKTABLE_NAME")).append("; ");
            }

            String tableIdentity = json.cleanContent(String.format(
                    "表 %s 字段定义及主外键关系",
                    tableName
            ));

            list.add(new Document(tableIdentity, Map.of("table_name", tableName, "column_info", columnInfo.toString(), "table_keys", fkInfo.toString(), "type", "字段定义")));
        }
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

