package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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

    private final Chater toolClient;

    public String toolCall(String prompt) {
        log.info("toolCall prompt {}", prompt);
        String ret = toolClient.call("你是一个协助用户完成工作的智能助手，如果不需要调用提供的工具能力，不要在回答时提及，直接回答问题即可。回答时格式工整，少用符号。",prompt);
        log.info("toolCall ret {}", ret);
        return ret;
    }

    public Flux<String> acknowledge(String requirement) {
        return progress.processMessage(requirement);
    }

    public String processFile(MultipartFile file) {
        List<Document> splitDocs = rerank.tikaReader(file);
        return splitDocs.stream().map(document ->
                llm.callUserStatement("请根据下列正文，提炼总结文章内容。回复中不要包含markdown格式。%s".formatted(document.getText()))).collect(Collectors.joining("\n"));
    }

    public String vectorize(MultipartFile file) {

        Resource resource = file.getResource();
        String filename = resource.getFilename();

        List<Document> splitDocs = rerank.tikaReader(file);

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
