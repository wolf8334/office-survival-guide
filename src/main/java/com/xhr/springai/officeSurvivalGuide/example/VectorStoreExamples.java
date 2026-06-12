package com.xhr.springai.officeSurvivalGuide.example;

import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreExamples {

    private final VectorStoreUtil vectorStoreUtil;

    /**
     * 示例 1: 单条件等值查询
     * 查询所有 isAbstract=1 的摘要文档
     */
    public List<Document> example1_singleCondition(String query) {
        var filter = vectorStoreUtil.eq("isAbstract", 1);
        return vectorStoreUtil.similaritySearch(query, 10, 0.5, filter);
    }

    /**
     * 示例 2: OR 多条件查询 - 跨文件检索
     * 在多个文件中搜索，任意文件匹配即可
     */
    public List<Document> example2_orMultipleFiles(String query) {
        var fileFilter = vectorStoreUtil.or("filename",
                List.of("财务制度.pdf", "人事手册.pdf", "行政规范.pdf"));
        return vectorStoreUtil.similaritySearch(query, 15, 0.5, fileFilter);
    }

    /**
     * 示例 3: OR 多条件查询 - 跨层级检索
     * 同时检索章级别(level=0)和节级别(level=1)的内容
     */
    public List<Document> example3_orMultipleLevels(String query) {
        var levelFilter = vectorStoreUtil.or("level", List.of(0, 1));
        return vectorStoreUtil.similaritySearch(query, 20, 0.5, levelFilter);
    }

    /**
     * 示例 4: AND 组合查询
     * 跨文件 AND 跨层级 AND 非摘要 —— 三个条件同时满足
     */
    public List<Document> example4_andCombination(String query) {
        var fileFilter = vectorStoreUtil.or("filename",
                List.of("财务制度.pdf", "报销规范.pdf"));
        var levelFilter = vectorStoreUtil.or("level", List.of(0, 1));
        var notAbstract = vectorStoreUtil.eq("isAbstract", 0);

        var combined = vectorStoreUtil.and(fileFilter, levelFilter, notAbstract);
        return vectorStoreUtil.similaritySearch(query, 20, 0.5, combined);
    }

    /**
     * 示例 5: 两阶段检索（完整实现）
     * 第一阶段：检索 isAbstract=1 的摘要层，定位相关章节
     * 第二阶段：在相关章节内检索具体内容(isAbstract=0)
     * 最后合并结果并去重
     */
    public List<Document> twoStageSearch(String query, int firstStageTopK, int secondStageTopK) {
        log.info("=== 两阶段检索开始 ===");

        var abstractFilter = vectorStoreUtil.eq("isAbstract", 1);
        List<Document> abstractDocs = vectorStoreUtil.similaritySearch(
                query, firstStageTopK, 0.5, abstractFilter);

        log.info("阶段 1 完成，找到 {} 条摘要文档", abstractDocs.size());

        if (abstractDocs.isEmpty()) {
            log.info("摘要层未找到相关文档，降级为普通检索");
            return vectorStoreUtil.similaritySearch(query, secondStageTopK, 0.5);
        }

        @SuppressWarnings("unchecked")
        Set<List<String>> relevantPaths = abstractDocs.stream()
                .map(doc -> (List<String>) doc.getMetadata().get("hierarchyPath"))
                .filter(path -> path != null && !path.isEmpty())
                .collect(Collectors.toSet());

        log.info("提取到 {} 个相关章节路径", relevantPaths.size());

        if (relevantPaths.isEmpty()) {
            return abstractDocs;
        }

        log.info("阶段 2: 在相关章节内检索具体内容 (isAbstract=0)");

        List<String> allNodeIds = relevantPaths.stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        log.info("相关 nodeId 数量：{}", allNodeIds.size());

        var pathFilter = vectorStoreUtil.or("hierarchyPath", allNodeIds);
        var contentFilter = vectorStoreUtil.eq("isAbstract", 0);
        var finalFilter = vectorStoreUtil.and(pathFilter, contentFilter);

        List<Document> contentDocs = vectorStoreUtil.similaritySearch(
                query, secondStageTopK, 0.5, finalFilter);

        log.info("阶段 2 完成，找到 {} 条内容文档", contentDocs.size());

        List<Document> allDocs = new ArrayList<>(abstractDocs);
        allDocs.addAll(contentDocs);

        allDocs = allDocs.stream()
                .collect(Collectors.toMap(
                        Document::getId,
                        doc -> doc,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new))
                .values()
                .stream()
                .toList();

        log.info("=== 两阶段检索完成，共 {} 条文档（已去重） ===", allDocs.size());
        return allDocs;
    }
}
