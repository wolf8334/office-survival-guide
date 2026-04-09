package com.xhr.springai.officeSurvivalGuide.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final JdbcTemplate jdbcTemplate;
    private final JSONUtil json;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${custom.rerank-name}")
    private String rerankName;

    @Value("${custom.maxToken}")
    private int maxToken;

    @Value("${custom.chunkSize}")
    private int chunkSize;

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
            // 获取原始文档列表中对应的索引
            int index = (int) result.get("index");
            double score = Double.parseDouble(result.get("relevance_score").toString());

            // 拿到原始文档并存入分数，方便后续分析
            Document originalDoc = documents.get(index);
            originalDoc.getMetadata().put("rerank_score", score);
            sortedDocs.add(originalDoc);
        }

        // 返回排序后的文档（此时 List 的顺序就是由高到低的相关度）
        return sortedDocs;
    }

    public List<Document> tikaReader(Resource resource){
        List<Document> finalChunks = new ArrayList<>();

        String filename = resource.getFilename();
        String fileType = getResourceType(resource);

        log.info("待向量化文件名: {}", filename);
        log.info("fileType {}",fileType);

        if ("ceb".equalsIgnoreCase(filename) && "application/octet-stream".equalsIgnoreCase(fileType)){
            return new ArrayList<>();
        }

        if ("pdf".equalsIgnoreCase(fileType)){
            // 如果是PDF文件，使用POFBox处理
            // 1. 初始化 PDF 读取器配置
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0).withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfTopTextLinesToDelete(0).build()).withPagesPerDocument(1).build();

            // 2. 创建读取器，加载资源
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);

            // 3. 获取按页划分的原始文档（一页为一个 Document 对象）
            List<Document> pages = pdfReader.get();

            for (Document page : pages) {
                // 获取当前页的页码元数据
                Object pageNum = page.getMetadata().get("page_number");

                // 使用统一的PageNum和filename
                page.getMetadata().remove("page_number");
                page.getMetadata().remove("file_name");

                page.getMetadata().put("pageNum", pageNum);
                page.getMetadata().put("filename", resource.getFilename());
                page.getMetadata().put("fileType", fileType);

                finalChunks.add(page);
            }
        } else {
            // 使用Tika读取并解析为 Document 对象列表
            TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
            List<Document> documents = tikaDocumentReader.read();
            int pageNum = 1;

            for (Document chunk : documents) {
                chunk.getMetadata().put("pageNum", pageNum++);
                chunk.getMetadata().put("filename", resource.getFilename());
                chunk.getMetadata().put("fileType", fileType);
                finalChunks.add(chunk);
            }
        }

        // 3. 文本转换：如果文件很大，需切分为模型可接受的 Token 块
        List<Document> splitDocs = split(finalChunks);
        log.info("转换文件完成,共{}组",splitDocs.size());

        return splitDocs;
    }

    public void addDocumentToMySQL(List<Document> documents){
        String clean = "delete from knowledge_chunks where doc_id = '%s'".formatted(documents.getFirst().getMetadata().get("filename"));
        jdbcTemplate.execute(clean);

        String sql = "insert into knowledge_chunks (id, doc_id, content, metadata) values ('%s','%s','%s','%s');";
        documents.forEach(doc -> jdbcTemplate.execute(sql.formatted(doc.getId(),doc.getMetadata().get("filename"),doc.getText(),json.parseObject(doc.getMetadata()))));
    }

    public String getResourceType(Resource resource){
        try (TikaInputStream tis = TikaInputStream.get(resource.getInputStream())) {
            return convertFiltTypeName(new Tika().detect(tis));
        } catch (IOException ignored) {
        }

        return "";
    }

    public List<Document> getFullDocument(List<Document> documents){
        String filename = documents.getFirst().getMetadata().get("filename").toString();
        List<Integer> pageNums = documents.stream().map(doc -> Integer.parseInt(doc.getMetadata().get("pageNum").toString())).distinct().toList();
        String sql = """
                select * from knowledge_chunks where metadata->>'$.filename' = '%s' and metadata->>'$.pageNum' in (%s)
                order by metadata->>'$.pageNum',metadata->>'$.chunk_index'
                """.formatted(filename,String.join(",",pageNums.stream().map(String::valueOf).collect(Collectors.joining(","))));
        log.info("getFullDocument {}",sql);

        return jdbcTemplate.queryForList(sql).stream().map(m -> {
            String id = m.get("id").toString();
            String content = m.get("content").toString();
            Map<String,Object> metadata = json.parseString(m.get("metadata").toString(), new TypeReference<>() {});
            return new Document(id,content,metadata);
        }).toList();
    }

    private String convertFiltTypeName(String mimeType) {
        return switch (mimeType) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "application/msword" -> "doc";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/pdf" -> "pdf";
            default -> mimeType;
        };
    }

    private List<Document> split(List<Document> docu){
        return TokenTextSplitter.builder()
                .withChunkSize(maxToken).withMinChunkSizeChars(chunkSize).withMinChunkLengthToEmbed(10).withMaxNumChunks(5000)
                .withKeepSeparator(true).withPunctuationMarks(List.of('。', '？', '！', '；','.', '?', '!', '\n', ';', ':'))
                .build().apply(docu);
    }

    private List<Document> split(Document docu){
        return TokenTextSplitter.builder()
                .withChunkSize(maxToken).withMinChunkSizeChars(chunkSize).withMinChunkLengthToEmbed(10).withMaxNumChunks(5000)
                .withKeepSeparator(true).withPunctuationMarks(List.of('。', '？', '！', '；','.', '?', '!', '\n', ';', ':'))
                .build().split(docu);
    }
}
