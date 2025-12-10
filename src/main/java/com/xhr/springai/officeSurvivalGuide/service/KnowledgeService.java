package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VectorStoreUtil vectorStore;


    public void refreshKnowledgeBase() {
        log.info("开始执行专家知识库全量刷新任务");

        String selectSql = "SELECT keyword,explanation FROM public.sys_expert_rules";
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
}

