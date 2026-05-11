package com.xhr.springai.officeSurvivalGuide.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhr.springai.officeSurvivalGuide.bean.MinerUContentItem;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PDFUtil {

    private static final Logger log = LoggerFactory.getLogger(PDFUtil.class);

    private final VLChater vl;
    private final MineruClient mineru;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Pattern pattern = Pattern.compile("[\\u4e00-\\u9fa5]");

    public List<Document> mineruReader(MultipartFile file,String fileType) {
        List<Document> docs = new ArrayList<>();
        String ret = mineru.call(file);
        log.info("使用MinerU解析文件返回{}",ret);

        if (StringUtils.isNoneEmpty(ret)){
            try {
                JsonNode root = mapper.readTree(ret);
                JsonNode fileNames = root.get("file_names");
                log.info("fileNames {}",fileNames);

                for (JsonNode fileName : fileNames) {
                    log.info("fileName {}",fileName);

                    String name = fileName.asText();
                    JsonNode contentNodes = root.path("results").path(name).path("content_list");
                    if (!contentNodes.isMissingNode()){
                        String content = contentNodes.asText();
                        List<MinerUContentItem> items = mapper.readValue(content,mapper.getTypeFactory().constructCollectionType(List.class, MinerUContentItem.class));

                        StringBuilder current = new StringBuilder();
                        int currentPage = 0;
                        String currentSection = "";
                        int chunkIndex = 0;

                        for (MinerUContentItem item : items) {
                            Integer level = item.getText_level();
                            boolean isHeading = level != null && level >= 1 && level <= 3;

                            if (isHeading && !current.isEmpty()) {
                                // 保存上一个 chunk
                                Document doc = new Document(current.toString().trim());
                                doc.getMetadata().put("filename", file.getResource().getFilename());
                                doc.getMetadata().put("fileType", fileType);
                                doc.getMetadata().put("section", currentSection);
                                doc.getMetadata().put("pageNum", currentPage);
                                doc.getMetadata().put("chunkIndex", chunkIndex++);
                                docs.add(doc);

                                //log.info("保存文件 {} 第 {} 页，标题为 {}",file.getResource().getFilename(),currentPage,currentSection);
                                current = new StringBuilder();
                            }

                            if (isHeading) {
                                currentSection = item.getText();
                                currentPage = item.getPage_idx();
                            }

                            if ("table".equalsIgnoreCase(item.getType()) && item.getTable_body() != null){
                                current.append(item.getTable_body()).append("\n");
                            }

                            if ("text".equalsIgnoreCase(item.getType()) && item.getText() != null){
                                current.append(item.getText()).append("\n");
                            }
                        }

                        // 最后一个 chunk
                        Document doc = new Document(current.toString().trim());
                        doc.getMetadata().put("filename", file.getResource().getFilename());
                        doc.getMetadata().put("fileType", fileType);
                        doc.getMetadata().put("section", currentSection);
                        doc.getMetadata().put("pageNum", currentPage);
                        doc.getMetadata().put("chunkIndex", chunkIndex);

                        //log.info("保存文件 {} 第 {} 页，标题为 {}",file.getResource().getFilename(),currentPage,currentSection);
                        docs.add(doc);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return docs;
    }

    public List<Document> readPDF(MultipartFile file, String fileType) {
        List<Document> list = new ArrayList<>();
        Resource resource = file.getResource();


        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageTopMargin(0).withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                        .withNumberOfTopTextLinesToDelete(0).build()).withPagesPerDocument(1).build();

        // 2. 创建读取器，加载资源
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);

        // 3. 获取按页划分的原始文档（一页为一个 Document 对象）
        List<Document> pages = pdfReader.get();

        log.info("读取PDF得到 {} 页", pages.size());

        if (pages.isEmpty() || needOCR(pages.getFirst())) {
            log.info("需要识别图片 {}", resource.getFilename());
            try {
                // 提取该页为图片
                File tempFile = File.createTempFile("pdf_", ".pdf");
                file.transferTo(tempFile);
                tempFile.deleteOnExit();

                List<String> docs = extractPageAsImage(tempFile.getAbsolutePath());

                int page_number = 1;
                for (String doc_content : docs) {
                    Map<String, Object> metadata = Map.of("chunk_index", 0, "pageNum", page_number++, "fileType", fileType, "total_chunks", 1);
                    list.add(new Document(doc_content, metadata));
                }
                log.info("文件 {} 识别完成", resource.getFilename());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            log.info("{} 不需要识别图片，共有{}组", resource.getFilename(), pages.size());

            for (Document page : pages) {
                // 获取当前页的页码元数据
                Object pageNum = page.getMetadata().get("page_number");

                // 使用统一的PageNum和filename
                page.getMetadata().remove("page_number");
                page.getMetadata().remove("file_name");

                page.getMetadata().put("pageNum", pageNum);
                page.getMetadata().put("filename", resource.getFilename());
                page.getMetadata().put("fileType", fileType);

                list.add(page);
            }
        }
        log.info("读取PDF得到{}组结果数据", list.size());
        return list;
    }

    /**
     * 判断当前PDF文件是否需要进行OCR扫描
     *
     * @param document 待分析文档
     *
     */
    private boolean needOCR(Document document) {
        String content = document.getText();

        // 1. 没内容，直接判定需要 OCR
        if (content == null || content.trim().isEmpty()) {
            return true;
        }

        // 2. 文字太少，极有可能是页码或杂讯，需要 OCR
        if (content.trim().length() < 20) {
            return true;
        }

        // 3. 文字太零散（分散度检测）
        String[] lines = content.split("\n");
        long validLines = Arrays.stream(lines)
                .filter(line -> !line.trim().isEmpty())
                .count();

        if (validLines > 0) {
            long singleCharLines = Arrays.stream(lines)
                    .filter(line -> line.trim().length() <= 2 && !line.trim().isEmpty())
                    .count();

            double singleCharRatio = (double) singleCharLines / validLines;
            log.info("有效行数: {}, 碎片行占比: {}", validLines, singleCharRatio);

            if (singleCharRatio > 0.3) {
                return true;
            }
        }

        // 4. 关键点：字符特征检测
        boolean hasChinese = pattern.matcher(content).find();
        log.info("是否包含中文字符: {}", hasChinese);

        return !hasChinese;
    }

    private List<String> extractPageAsImage(String pdfPath) throws IOException {
        int totalPage = getPDFPages(pdfPath);

        Semaphore semaphore = new Semaphore(3);

        List<CompletableFuture<String>> futures = IntStream.range(0, totalPage)
                .<CompletableFuture<String>>mapToObj(pageIndex -> CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try (PDDocument docu = Loader.loadPDF(new File(pdfPath))) {
                            var renderer = new PDFRenderer(docu);
                            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);
                            String result = vl.call("请识别此图片，返回图片上的内容", imageToBase64(image));
                            image.flush(); // 显式释放内存
                            log.info("pageIndex {} 共 {}",pageIndex,totalPage);
                            return result;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }catch (IOException _) {
                        return null;
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        return futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
    }

    private int getPDFPages(String pdfPath) {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {
            return document.getNumberOfPages();
        } catch (IOException _) {
        }
        return 0;
    }

    private String imageToBase64(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }
}
