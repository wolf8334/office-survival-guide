package com.xhr.springai.officeSurvivalGuide.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${custom.rerank-name}")
    private String rerankName;

    public List<Document> rerank(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        RestClient restClient = RestClient.builder().baseUrl(baseUrl + "v1/rerank").build();

        List<String> docContents = documents.stream().map(Document::getText).toList();

        Map<String, Object> requestBody = Map.of(
                "model", rerankName,
                "query", query,
                "documents", docContents,
                "top_n", 5,
                "return_documents", false
        );

        log.info("调用rerank {}",rerankName);

        Map<String, Object> response = restClient.post()
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        log.info("rerank返回 {}",response);

        // 处理返回结果 (results 包含 index 和 relevance_score)
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        List<Document> sortedDocs = new ArrayList<>();

        for (Map<String, Object> result : results) {
            // 重要：获取原始文档列表中对应的索引
            int index = (int) result.get("index");
            double score = Double.parseDouble(result.get("relevance_score").toString());

            // 拿到原始文档并存入分数，方便后续分析
            Document originalDoc = documents.get(index);
            originalDoc.getMetadata().put("rerank_score", score);
            sortedDocs.add(originalDoc);
        }

        // 5. 返回排序后的文档（此时 List 的顺序就是由高到低的相关度）
        return sortedDocs;
    }
}
