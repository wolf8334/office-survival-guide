package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.LLMUtil;
import com.xhr.springai.officeSurvivalGuide.util.ProgressUtil;
import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HQService {

    private final ProgressUtil progress;
    private final LLMUtil llm;
    private final JSONUtil json;
    private final VectorStoreUtil vector;
    private final RerankService rerank;

    public Flux<String> acknowledge(String requirement) {
        return progress.processMessage(requirement);
    }

    public String processFile(Resource resource) {
        List<Document> splitDocs = rerank.tikaReader(resource);
        return splitDocs.stream().map(document ->
                llm.callUserStatement("请根据下列正文，提炼总结文章内容。回复中不要包含markdown格式。%s".formatted(document.getText()))).collect(Collectors.joining("\n"));
    }

    public String vectorize(Resource resource) {
        String filename = resource.getFilename();
        List<Document> splitDocs = rerank.tikaReader(resource);

        List<Document> list = new ArrayList<>();
        splitDocs.forEach(document -> {
            if (document.getText() != null) {
                String uuid = UUID.randomUUID().toString();
                String text = json.cleanContent(document.getText());
                document.getMetadata().putAll(Map.of("filename", Objects.requireNonNull(filename), "type", "知识库文件导入"));

                list.add(new Document(uuid,text, document.getMetadata()));
            }
        });
        log.info("文档 {} 生成完毕",filename);

        vector.delete("type == '知识库文件导入' && filename == '%s'".formatted(filename));
        log.info("文档 {} 清理完毕",filename);

        vector.add(list);
        log.info("文档 {} 向量化完毕",filename);

        //将切片的document入库MySQL
        rerank.addDocumentToMySQL(list);
        log.info("文档 {} 入库完毕",filename);

        return "文档入库完成";
    }
}
