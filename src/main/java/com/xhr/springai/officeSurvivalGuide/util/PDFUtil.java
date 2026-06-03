package com.xhr.springai.officeSurvivalGuide.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhr.springai.officeSurvivalGuide.bean.HierarchyNode;
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

    public List<Document> mineruReader(MultipartFile file, String fileType) {
        List<Document> docs = new ArrayList<>();
        String ret = mineru.call(file);
        log.info("使用MinerU解析文件完成");

        if (StringUtils.isNoneEmpty(ret)) {
            try {
                JsonNode root = mapper.readTree(ret);
                JsonNode fileNames = root.get("file_names");
                log.info("fileNames {}", fileNames);

                for (JsonNode fileName : fileNames) {
                    log.info("fileName {}", fileName);

                    String name = fileName.asText();
                    JsonNode contentNodes = root.path("results").path(name).path("content_list");
                    if (!contentNodes.isMissingNode()) {
                        String content = contentNodes.asText();
                        List<MinerUContentItem> items = mapper.readValue(
                                content,
                                mapper.getTypeFactory().constructCollectionType(List.class, MinerUContentItem.class)
                        );

                        String filename = file.getResource().getFilename();
                        docs.addAll(buildHierarchicalChunks(items, filename, fileType));
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

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
        List<Document> pages = pdfReader.get();

        log.info("读取PDF得到 {} 页", pages.size());

        if (pages.isEmpty() || needOCR(pages.getFirst())) {
            log.info("需要识别图片 {}", resource.getFilename());
            try {
                File tempFile = File.createTempFile("pdf_", ".pdf");
                file.transferTo(tempFile);
                tempFile.deleteOnExit();

                List<String> docs = extractPageAsImage(tempFile.getAbsolutePath());

                int page_number = 1;
                for (String doc_content : docs) {
                    Map<String, Object> metadata = Map.of(
                            "chunk_index", 0,
                            "pageNum", page_number++,
                            "fileType", fileType,
                            "total_chunks", 1
                    );
                    list.add(new Document(doc_content, metadata));
                }
                log.info("文件 {} 识别完成", resource.getFilename());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            log.info("{} 不需要识别图片，共有{}组", resource.getFilename(), pages.size());

            for (Document page : pages) {
                Object pageNum = page.getMetadata().get("page_number");
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

    private boolean needOCR(Document document) {
        String content = document.getText();

        if (content == null || content.trim().isEmpty()) return true;
        if (content.trim().length() < 20) return true;

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
            if (singleCharRatio > 0.3) return true;
        }

        boolean hasChinese = pattern.matcher(content).find();
        log.info("是否包含中文字符: {}", hasChinese);
        return !hasChinese;
    }

    private List<String> extractPageAsImage(String pdfPath) throws IOException {
        int totalPage = getPDFPages(pdfPath);
        Semaphore semaphore = new Semaphore(3);

        List<CompletableFuture<String>> futures = IntStream.range(0, totalPage)
                .mapToObj(pageIndex -> CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try (PDDocument docu = Loader.loadPDF(new File(pdfPath))) {
                            var renderer = new PDFRenderer(docu);
                            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);
                            String result = vl.call("请识别此图片，返回图片上的内容", imageToBase64(image));
                            image.flush();
                            log.info("pageIndex {} 共 {}", pageIndex, totalPage);
                            return result;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    } catch (IOException _) {
                        return null;
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        return futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
    }

    /**
     * 层级索引
     * */
    private List<Document> buildHierarchicalChunks(List<MinerUContentItem> items, String filename, String fileType) {
        Map<String, HierarchyNode> hierarchy = extractHierarchy(items);
        return convertToDocuments(hierarchy, filename, fileType);
    }

    private Map<String, HierarchyNode> extractHierarchy(List<MinerUContentItem> items) {
        Map<String, HierarchyNode> hierarchy = new LinkedHashMap<>();
        HierarchyNode currentChapter = null;  // level 0
        HierarchyNode currentSection = null;  // level 1
        HierarchyNode currentSub = null;      // level 2

        for (MinerUContentItem item : items) {
            Integer textLevel = item.getText_level();
            boolean isHeading = textLevel != null && textLevel >= 1 && textLevel <= 3;

            if (isHeading) {
                // MinerU text_level 1/2/3 → 内部层级 0/1/2
                int level = textLevel - 1;
                String nodeId = "node_" + UUID.randomUUID();
                String title = item.getText() == null ? "" : item.getText().strip();

                if (level == 0) {
                    HierarchyNode node = new HierarchyNode(nodeId, title, 0, null, List.of(nodeId));
                    hierarchy.put(nodeId, node);
                    currentChapter = node;
                    currentSection = null;
                    currentSub = null;

                } else if (level == 1) {
                    String parentId = currentChapter != null ? currentChapter.getId() : null;
                    List<String> path = currentChapter != null
                            ? appendPath(currentChapter.getHierarchyPath(), nodeId)
                            : List.of(nodeId);
                    HierarchyNode node = new HierarchyNode(nodeId, title, 1, parentId, path);
                    hierarchy.put(nodeId, node);
                    if (currentChapter != null) currentChapter.getChildrenIds().add(nodeId);
                    currentSection = node;
                    currentSub = null;

                } else {
                    // level == 2
                    HierarchyNode parent = currentSection != null ? currentSection : currentChapter;
                    String parentId = parent != null ? parent.getId() : null;
                    List<String> path = parent != null
                            ? appendPath(parent.getHierarchyPath(), nodeId)
                            : List.of(nodeId);
                    HierarchyNode node = new HierarchyNode(nodeId, title, 2, parentId, path);
                    hierarchy.put(nodeId, node);
                    if (parent != null) parent.getChildrenIds().add(nodeId);
                    currentSub = node;
                }

            } else {
                // 正文/表格，追加到当前最深节点
                String text = "";
                if ("table".equalsIgnoreCase(item.getType()) && item.getTable_body() != null) {
                    text = item.getTable_body();
                } else if ("text".equalsIgnoreCase(item.getType()) && item.getText() != null) {
                    text = item.getText();
                }

                if (!text.isEmpty()) {
                    HierarchyNode target = currentSub != null ? currentSub
                            : currentSection != null ? currentSection
                              : currentChapter;
                    if (target != null) {
                        target.getContent().append(text).append("\n");
                    }
                }
            }
        }

        return hierarchy;
    }

    private List<Document> convertToDocuments(Map<String, HierarchyNode> hierarchy, String filename, String fileType) {
        List<Document> docs = new ArrayList<>();
        int pageNum = 1;

        // 叶子节点 → 正文 chunk
        for (HierarchyNode node : hierarchy.values()) {
            if (node.getChildrenIds().isEmpty()) {
                String text = node.getTitle() + "\n\n" + node.getContent().toString().trim();
                Document doc = new Document(text);
                doc.getMetadata().put("filename", filename);
                doc.getMetadata().put("fileType", fileType);
                doc.getMetadata().put("pageNum", pageNum++);
                doc.getMetadata().put("section", node.getTitle());
                doc.getMetadata().put("level", node.getLevel());
                doc.getMetadata().put("nodeId", node.getId());
                doc.getMetadata().put("hierarchyPath", node.getHierarchyPath());
                doc.getMetadata().put("isAbstract", 0);
                docs.add(doc);
            }
        }

        // 根节点额外生成摘要 chunk，检索时可先查这一层定位章节
        for (HierarchyNode node : hierarchy.values()) {
            if (node.getLevel() == 0) {
                String text = node.getTitle() + "\n\n" + node.getContent().toString().trim();
                Document doc = new Document(text);
                doc.getMetadata().put("filename", filename);
                doc.getMetadata().put("fileType", fileType);
                doc.getMetadata().put("pageNum", 0);
                doc.getMetadata().put("section", node.getTitle());
                doc.getMetadata().put("level", 0);
                doc.getMetadata().put("nodeId", node.getId());
                doc.getMetadata().put("hierarchyPath", node.getHierarchyPath());
                doc.getMetadata().put("isAbstract", 1);
                docs.add(doc);
            }
        }

        log.info("层级索引完成，共生成 {} 个 chunk", docs.size());
        return docs;
    }

    private List<String> appendPath(List<String> base, String nodeId) {
        List<String> path = new ArrayList<>(base);
        path.add(nodeId);
        return path;
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