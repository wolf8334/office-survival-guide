package com.xhr.springai.officeSurvivalGuide.util;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
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
        return similaritySearch(requirement,-1,-1,(Filter.Expression)null);
    }

    public List<Document> similaritySearch(@NotNull String requirement, int topk) {
        return similaritySearch(requirement,topk,-1,(Filter.Expression)null);
    }

    public List<Document> similaritySearch(@NotNull String requirement, double threshold) {
        return similaritySearch(requirement,-1,threshold,(Filter.Expression)null);
    }

    public List<Document> similaritySearch(@NotNull String requirement,String filter) {
        return similaritySearch(requirement,-1,-1,filter);
    }

    public List<Document> similaritySearch(@NotNull String requirement, int topk,String filter) {
        return similaritySearch(requirement,topk,-1,filter);
    }

    public List<Document> similaritySearch(@NotNull String requirement, double threshold,String filter) {
        return similaritySearch(requirement,-1,threshold,filter);
    }

    public List<Document> similaritySearch(@NotNull String requirement, int topk, double threshold) {
        return similaritySearch(requirement,topk,threshold,(Filter.Expression)null);
    }

    public List<Document> similaritySearch(@NotNull String requirement, int topk, double threshold, Filter.Expression filterExpression) {
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

        if (filterExpression != null) {
            similaritySearchBuilder.filterExpression(filterExpression);
        }

        return vectorStore.similaritySearch(similaritySearchBuilder.build());
    }

    public List<Document> similaritySearch(@NotNull String requirement, int topk, double threshold,String filter) {
        if (filter == null) {
            return similaritySearch(requirement, topk, threshold, (Filter.Expression)null);
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return similaritySearch(requirement, topk, threshold, b.eq("type",filter).build());
    }

    public Filter.Expression eq(String key, Object value) {
        return new FilterExpressionBuilder().eq(key, value).build();
    }

    public Filter.Expression in(String key, Object... values) {
        return new FilterExpressionBuilder().in(key, values).build();
    }

    public Filter.Expression or(String key, List<?> values) {
        if (values == null || values.isEmpty()) return null;
        return new FilterExpressionBuilder().in(key, new ArrayList<>(values)).build();
    }

    public Filter.Expression or(Filter.Expression... expressions) {
        if (expressions == null || expressions.length == 0) return null;
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression result = expressions[0];
        for (int i = 1; i < expressions.length; i++) {
            result = b.or(new FilterExpressionBuilder.Op(result), new FilterExpressionBuilder.Op(expressions[i])).build();
        }
        return result;
    }

    public Filter.Expression and(Filter.Expression... expressions) {
        if (expressions == null || expressions.length == 0) return null;
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression result = expressions[0];
        for (int i = 1; i < expressions.length; i++) {
            result = b.and(new FilterExpressionBuilder.Op(result), new FilterExpressionBuilder.Op(expressions[i])).build();
        }
        return result;
    }
}
