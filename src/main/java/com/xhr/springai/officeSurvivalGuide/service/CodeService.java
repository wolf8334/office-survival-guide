package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.bean.FileItem;
import com.xhr.springai.officeSurvivalGuide.bean.GenerateCode;
import com.xhr.springai.officeSurvivalGuide.bean.GenerateResult;
import com.xhr.springai.officeSurvivalGuide.bean.SqlIr;
import com.xhr.springai.officeSurvivalGuide.client.ChaterClient;
import com.xhr.springai.officeSurvivalGuide.client.CoderClient;
import com.xhr.springai.officeSurvivalGuide.util.CodeSplitter;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.SqlParamExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeService {

    private final CodeSplitter codeWriter;
    private final JSONUtil json;
    private final SqlParamExtractor sqlParamExtractor;
    private final TempStorage tempStorage;
    private final ChaterClient chat;
    private final CoderClient code;

    public String writeCode(Map<String, String> param) {
        try {
            String sql = param.getOrDefault("sql", "");
            String uriPath = param.getOrDefault("uriPath", "");
            String busiName = param.getOrDefault("busiName", "");
            String busiChnName = param.getOrDefault("busiChnName", "");
            String basePackage = param.getOrDefault("basePackage", "");

            if (sql.trim().isEmpty() || uriPath.trim().isEmpty() || busiName.trim().isEmpty() || busiChnName.trim().isEmpty()) {
                return "用户输入不合理";
            }

            log.info("调用writeCode {}", param);

            List<String> whereParams = sqlParamExtractor.extractWhereParams(sql);
            List<SqlIr.ColumnInfo> columnInfoList = sqlParamExtractor.getTableColumns(sql);

            SqlIr sqlIr = new SqlIr();
            sqlIr.setWhere(whereParams);
            sqlIr.setColumns(columnInfoList);

            String sqlToIR = json.parseObject(sqlIr);
            log.info("sqlIr {}", sqlToIR);

            String controllerTemplate = Files.readString(Paths.get("reference/DemoController.java"));
            String ServiceInterfaceTemplate = Files.readString(Paths.get("reference/IDemoRoomOrderService.java"));
            String ServiceTemplate = Files.readString(Paths.get("reference/DemoRoomOrderServiceImpl.java"));
            String daoTemplate = Files.readString(Paths.get("reference/DemoRoomOrderDao.java"));
            String mapperTemplate = Files.readString(Paths.get("reference/DemoRoomOrderMapper.xml"));


            String webapiTemplate = Files.readString(Paths.get("reference/DemoRoomOrderAPI.js"));
            String webrouteTemplate = Files.readString(Paths.get("reference/DemoRoomOrderRoute.js"));
            String webpageTemplate = Files.readString(Paths.get("reference/roomInfo.vue"));

            String systemPromot = """
                    你是代码生成工具，直接输出代码，不要任何解释。
                    项目用到的技术有SpringBoot2，MyBatis Plus，Vue2，iView。
                    输出格式：每个文件前一行写 ------文件名，紧接着是文件完整内容。
                    Vue2 api文件的文件名需要以Api.js结尾,vue2 route文件的文件名要以Route.js结尾。
                    table组件的getSearch方法第二个参数传url字符串，不要传函数引用，格式如下：
                    this.$refs.table.getSearch(formData, '/${uriPath}/list');"
                    service实现类的引用要加入实现的接口类
                    第一行必须是 ------文件名，不能有任何前言。
                    """;

            String userPrompt = """
                    原始SQL
                    %s
                    
                    SQL解析结果
                    %s
                    
                    请求路径：%s
                    功能模块名：%s
                    功能中文名：%s
                    java代码包名： %s
                    代码生成时间：当前时间。
                    
                     %s
                     ------controller模板
                     %s
                     ------service接口模板
                     %s
                     ------service实现类模板
                     %s
                     ------dao模板
                     %s
                     ------mapper.xml模板
                     %s
                     ------vue2 api 模板
                     %s
                     ------vue2 route 模板
                     %s
                     ------vue2 页面模板
                    
                    按照以上模板格式，直接输出完整代码，第一行就是------加文件名。
                    """.formatted(sql, sqlToIR, uriPath, busiName, busiChnName, basePackage, controllerTemplate, ServiceInterfaceTemplate, ServiceTemplate, daoTemplate, mapperTemplate, webapiTemplate, webrouteTemplate, webpageTemplate);
            String files = code.call(systemPromot, userPrompt).getContent();

            String javaBase = "C:\\Users\\Administrator\\IdeaProjects\\smarcatering-srv\\src\\main\\java\\com\\suypower\\inteCater\\foodBeverages";
            String resourceBase = "C:\\Users\\Administrator\\IdeaProjects\\smarcatering-srv\\src\\main\\resources\\mapper\\inteCater\\foodBeverages";
            String vueBase = "C:\\Users\\Administrator\\IdeaProjects\\srvAssurance\\src";
            //codeWriter.writeFiles(codeWriter.split(files),javaBase,resourceBase,vueBase,uriPath,busiName,busiChnName);

            return "代码已写入";
        } catch (IOException | JSQLParserException ignored) {
        }
        return "默认代码生成结果";
    }

    public String analyze(@RequestBody Map<String, String> param) {
        log.info("分析用户意图 {}", param);
        String prompt = param.getOrDefault("prompt", "");

        if (prompt.isEmpty()) {
            return "未输入有效信息，请填写";
        }

        String systemMessage = """
                你是一个代码生成工具，项目使用Java 8 作为基础运行时，是B/S结构的后端程序及前端页面，数据库使用MySQL。
                判断用户的需求是否足够生成一套完整的 SpringBoot + MyBatis + Vue 代码。
                需要的信息：表名或表结构、业务名称、包名、请求路径。
                参考文件可以补充风格，但不是必须的。
                """;
        String userMessage = """
                用户需求：%s
                
                如果信息足够：
                返回 JSON：{"canGenerate": true, "summary": "用一句话总结你理解的需求"}
                
                如果信息不足：
                返回 JSON：{"canGenerate": false, "reason": "说明缺少什么信息"}
                
                只返回 JSON，不要任何其他内容。
                """.formatted(prompt);

        return chat.call(systemMessage,userMessage).getContent();
    }

    public GenerateResult writeFullCode(@RequestBody GenerateCode genCode) {
        try {
            log.info("编写代码 {}", json.parseObject(genCode));
            //用户首次给大模型输入的信息
            String prompt = genCode.getPrompt();

            //大模型首次给用户的返回
            String analysis = genCode.getAnalysis();

            //用户选择的模型
            String model = genCode.getModel();

            //用户上传的文件
            List<FileItem> list = genCode.getFiles();

            String techRequirements = genCode.getTechRequirements();

            String systemMessage = """
                    你是代码生成工具，直接输出代码，不要任何解释。
                    项目使用Java 8 作为基础运行时，是B/S结构的后端程序及前端页面，数据库使用MySQL。
                    
                    技术栈使用SpringBoot2，MyBatis Plus，Vue2，iView，生成一套完整可运行的代码。
                    必须包括前端和后端代码。生成完整代码后，生成一个README.md文件，说明技术选型原因及代码文件应存放的目录
                    
                    输出格式：每个文件前一行写 ------文件名，紧接着是文件完整内容。
                    如果你认为无法生成完整代码，必须说明具体原因，不能直接停止输出。
                    
                    %s
                    
                    经过整理的用户需求是
                    %s
                    """.formatted(techRequirements,analysis);

            String userMessage = """
                    用户输入信息是
                    %s
                    
                    用户提供的文件示例是
                    %s
                    """.formatted(prompt, json.parseObject(list));

            String ret = code.call(systemMessage, userMessage).getContent();

            Map<String, String> files = codeWriter.split(ret);

            String token = UUID.randomUUID().toString();
            byte[] zipBytes = zipFiles(files);
            tempStorage.put(token, zipBytes);  // 存内存或者临时文件

            List<Map<String, String>> fileList = files.keySet().stream()
                    .map(name -> Map.of("name", name))
                    .collect(Collectors.toList());

            log.info("文件下载token {}", token);
            log.info("已生成的文件 {}", fileList);

            return new GenerateResult(fileList, "/coder/generate/download?token=" + token, token);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private byte[] zipFiles(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    public byte[] download(String token) {
        log.info("通过token {} 下载文件", token);
        return tempStorage.get(token);
    }

    public byte[] downloadFile(String token, String name) {
        byte[] zip = tempStorage.get(token);

        // 从 zip 里找到对应文件
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return zis.readAllBytes();
                }
            }
        } catch (IOException ignored) {
        }

        return null;
    }
}
