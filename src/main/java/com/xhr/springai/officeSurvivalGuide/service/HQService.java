package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.LLMUtil;
import com.xhr.springai.officeSurvivalGuide.util.ProgressUtil;
import com.xhr.springai.officeSurvivalGuide.util.VectorStoreUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;


    @Value("${custom.maxToken}")
    private int maxToken;

    @Value("${custom.chunkSize}")
    private int chunkSize;

    public Flux<String> acknowledge(String requirement) {
        return progress.processMessage(requirement);
    }

    public String processFile(Resource resource) {
        List<Document> splitDocs = tikaReader(resource);
        return splitDocs.stream().map(document ->
                llm.callUserStatement("请根据下列正文，提炼总结文章内容。回复中不要包含markdown格式。%s".formatted(document.getText()))).collect(Collectors.joining("\n"));
    }

    public String vectorize(Resource resource) {
        String filename = resource.getFilename();
        List<Document> splitDocs = tikaReader(resource);

        List<Document> list = new ArrayList<>();
        splitDocs.forEach(document -> {
            if (document.getText() != null) {
                String uuid = UUID.randomUUID().toString();
                list.add(new Document(uuid,json.cleanContent(document.getText()), Map.of("filename", Objects.requireNonNull(filename), "type", "知识库文件导入")));
            }
        });
        log.info("文档{}生成完毕",filename);

        vector.delete("type == '知识库文件导入' && filename == '%s'".formatted(filename));
        log.info("文档{}清理完毕",filename);
        vector.add(list);
        log.info("文档{}向量化完毕",filename);

        //将切片的document入库MySQL
        addDocumentToMySQL(list);
        log.info("文档{}入库完毕",filename);

        return "文档入库完成";
    }

    private List<Document> tikaReader(Resource resource){
        // 使用Tika读取文件
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
        String filename = resource.getFilename();

        // 读取并解析为 Document 对象列表
        List<Document> documents = tikaDocumentReader.read();
        log.info("待向量化文件名: {}", filename);

        // 3. 文本转换：如果文件很大，需切分为模型可接受的 Token 块
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(maxToken)
                .withMinChunkSizeChars(chunkSize)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(5000)
                .withKeepSeparator(true)
                .withPunctuationMarks(List.of('。', '？', '！', '；','.', '?', '!', '\n', ';', ':'))
                .build();
        List<Document> splitDocs = splitter.apply(documents);
        log.info("转换文件完成,共{}组",splitDocs.size());

        return splitDocs;
    }

    private void addDocumentToMySQL(List<Document> documents){
        String sql = "insert into knowledge_chunks (id, doc_id, content, metadata) values ('%s','%s','%s','%s');";
        documents.forEach(doc -> jdbcTemplate.execute(sql.formatted(doc.getId(),doc.getMetadata().get("filename"),doc.getText(),json.parseObject(doc.getMetadata()))));
    }


}
