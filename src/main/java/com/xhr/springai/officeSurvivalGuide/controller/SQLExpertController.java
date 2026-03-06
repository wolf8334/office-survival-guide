package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.service.SQLExpertService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@RestController()
@RequestMapping("/api/sqlexpert")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SQLExpertController {

    private static final Logger log = LoggerFactory.getLogger(SQLExpertController.class);

    private final SQLExpertService service;

    @PostMapping("/writeSQL")
    public Result<CommonData> writeSQL(@RequestBody Map<String, String> requirements) {
        String userRequirement = requirements.get("userRequirement");

        log.info("用户输入是 {}",userRequirement);
        return service.writeSomeSQL(userRequirement);
    }

    @GetMapping("/exportcsv")
    public void exportCsv(HttpServletResponse response) throws IOException {
        // 1. 获取数据 (假设你从 Service 获取了 List<Map>)
        // 注意：9w条 1024维向量的 List<Map> 约占 500MB+ 内存，请确保 JVM -Xmx 足够
        List<Map<String, Object>> dataList = service.getVector();

        // 2. 设置响应头
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"analysis_data.csv\"");

        // 3. 写入文件（添加 BOM 头防止 Excel 乱码）
        try (PrintWriter writer = response.getWriter()) {
            writer.write('\ufeff');

            // 写入表头
            writer.println("id,label,context,vector");

            // 4. 遍历 List 并处理格式
            for (Map<String, Object> row : dataList) {
                String id = String.valueOf(row.getOrDefault("id", ""));

                // 处理标签（短的）
                String label = escapeCsv(String.valueOf(row.getOrDefault("keyword", "")));

                // 处理原文（长的）并进行简单清洗，防止噪音
                String context = cleanContent(String.valueOf(row.getOrDefault("explanation", "")));
                context = escapeCsv(context);

                // 处理向量格式 {} -> []
                String vector = String.valueOf(row.getOrDefault("embedding", "[]"))
                        .replace('{', '[')
                        .replace('}', ']');

                // 格式化输出：ID,"标签","清洗后的原文","[向量]"
                writer.printf("%s,\"%s\",\"%s\",\"%s\"%n", id, label, context, vector);

                // 建议每 1000 行刷一次流，减少内存堆积
                if (dataList.indexOf(row) % 1000 == 0) {
                    writer.flush();
                }
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private String cleanContent(String content) {
        if (content == null) return "";
        return content
                .replaceAll("\\r|\\n", " ")           // 移除换行符，防止 CSV 格式崩溃
                .replaceAll("微信订阅号.*|责任编辑.*", "") // 移除末尾无关的推广噪音
                .replaceAll("ID:\\s*\\w+", "")         // 移除 ID 等随机干扰字符
                .trim();
    }
}
