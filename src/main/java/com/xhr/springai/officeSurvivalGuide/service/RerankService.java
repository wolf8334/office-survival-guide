package com.xhr.springai.officeSurvivalGuide.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.PDFUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {

    private final JdbcTemplate jdbcTemplate;
    private final JSONUtil json;
    private final PDFUtil pdf;

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

    @Value("${custom.hasGPU:false}")
    private String hasGPU;

    private final Set<String> MINERU_SUPPORTED = Set.of(
            "pdf", "docx", "pptx", "xlsx", "jpg", "jpeg", "png", "html"
    );

    public List<Document> rerank(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        RestClient restClient = RestClient.builder().baseUrl(baseUrl + "/v1/rerank").build();

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

        //log.info("rerank返回 {}",response);

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

    public List<Document> tikaReader(MultipartFile file){
        List<Document> finalChunks = new ArrayList<>();

        Resource resource = file.getResource();
        String filename = resource.getFilename();
        String fileType = getResourceType(resource);

        log.info("待向量化文件名: {}", filename);
        log.info("fileType {}",fileType);

        if ("ceb".equalsIgnoreCase(filename) && "application/octet-stream".equalsIgnoreCase(fileType)){
            return new ArrayList<>();
        }

        if ("true".equalsIgnoreCase(hasGPU) && MINERU_SUPPORTED.contains(fileType)){
            finalChunks = pdf.mineruReader(file,fileType);
        } else if ("pdf".equalsIgnoreCase(fileType)){
            finalChunks = pdf.readPDF(file,fileType);
        }else {
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

        String sql = "insert into knowledge_chunks (id, doc_id, content, metadata) values (?,?,?,?);";
        documents.forEach(doc -> {
            try {
                jdbcTemplate.update(sql,doc.getId(),doc.getMetadata().get("filename"), doc.getText(),json.parseObject(doc.getMetadata()));
            } catch (Exception e){
                log.info("metadata {}",json.parseObject(doc.getMetadata()));
                e.printStackTrace();
            }
        });
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
                select * from knowledge_chunks where metadata->>'$.filename' = '%s' and metadata->>'$.pageNum' + 0 in (%s)
                order by metadata->>'$.pageNum' + 0,metadata->>'$.chunk_index' + 0
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
            case "image/apng" -> "apng";
            case "image/avif" -> "avif";
            case "image/gif" -> "gif";
            case "image/jpeg" -> "jpeg";
            case "image/png" -> "png";
            case "image/svg+xml" -> "svg";
            case "image/webp" -> "webp";
            case "audio/wave", "audio/wav", "audio/x-wav", "audio/x-pn-wav" -> "wav";
            case "audio/webm","video/webm" -> "webm";
            case "audio/ogg","video/ogg","application/ogg" -> "ogg";
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
