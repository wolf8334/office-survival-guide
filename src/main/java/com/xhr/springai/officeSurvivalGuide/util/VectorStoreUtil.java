package com.xhr.springai.officeSurvivalGuide.util;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorStoreUtil {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreUtil.class);

    private final VectorStore vectorStore;

    public void add(List<Document> documents){
        int batchSize = 50;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);

            vectorStore.add(batch);

            log.info("已成功处理: {} / {}", end, documents.size());
        }
    }

    public void accept(List<Document> documents) {
        add(documents);
    }

    public void delete(List<String> idList) {
        vectorStore.delete(idList);
    }

    public void delete(Filter.Expression filterExpression){
        vectorStore.delete(filterExpression);
    }

    public void delete(String filterExpression) {
        SearchRequest searchRequest = SearchRequest.builder().filterExpression(filterExpression).build();
        Filter.Expression textExpression = searchRequest.getFilterExpression();
        Assert.notNull(textExpression, "过滤条件不能为空");
        this.delete(textExpression);
    }

    public List<Document> similaritySearch(@NotNull String requirement) {
        return this.similaritySearch(requirement,-1,-1);
    }

    public List<Document> similaritySearch(@NotNull String requirement, int topk) {
        return this.similaritySearch(requirement,topk,-1);
    }

    public List<Document> similaritySearch(@NotNull String requirement, double threshold) {
        return this.similaritySearch(requirement,-1,threshold);
    }


    public List<Document> similaritySearch(@NotNull String requirement, int topk, double threshold) {
        SearchRequest.Builder similaritySearchBuilder = SearchRequest.builder().query(requirement);

        if (topk > 0) {
            similaritySearchBuilder.topK(topk);
        } else {
            log.info("topk无效，忽略");
        }

        if (threshold >= 0 && threshold <= 1) {
            similaritySearchBuilder.similarityThreshold(threshold);
        } else {
            log.info("threshold无效，忽略");
        }

        return vectorStore.similaritySearch(similaritySearchBuilder.build());
    }
}
