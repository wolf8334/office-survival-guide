package com.xhr.springai.officeSurvivalGuide.service;

import com.xhr.springai.officeSurvivalGuide.bean.SqlIr;
import com.xhr.springai.officeSurvivalGuide.util.CodeSplitter;
import com.xhr.springai.officeSurvivalGuide.util.CoderUtil;
import com.xhr.springai.officeSurvivalGuide.util.JSONUtil;
import com.xhr.springai.officeSurvivalGuide.util.SqlParamExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeService {

    private final CoderUtil coder;
    private final CodeSplitter codeWriter;
    private final JSONUtil json;
    private final SqlParamExtractor sqlParamExtractor;

    public String writeCode(Map<String, String> param) {
        try {
            String sql = param.getOrDefault("sql","");
            String uriPath = param.getOrDefault("uriPath","");
            String busiName = param.getOrDefault("busiName","");
            String busiChnName = param.getOrDefault("busiChnName","");
            String basePackage = param.getOrDefault("basePackage","");

            if (sql.trim().isEmpty() || uriPath.trim().isEmpty() || busiName.trim().isEmpty() || busiChnName.trim().isEmpty()){
                return "用户输入不合理";
            }

            log.info("调用writeCode {}",param);

            List<String> whereParams = sqlParamExtractor.extractWhereParams(sql);
            List<SqlIr.ColumnInfo> columnInfoList = sqlParamExtractor.getTableColumns(sql);

            SqlIr sqlIr = new SqlIr();
            sqlIr.setWhere(whereParams);
            sqlIr.setColumns(columnInfoList);

            String sqlToIR = json.parseObject(sqlIr);
            log.info("sqlIr {}",sqlToIR);

            String controllerTemplate = Files.readString(Paths.get("reference/DemoController.java"));
            String ServiceInterfaceTemplate = Files.readString(Paths.get("reference/IDemoRoomOrderService.java"));
            String ServiceTemplate = Files.readString(Paths.get("reference/DemoRoomOrderServiceImpl.java"));
            String daoTemplate = Files.readString(Paths.get("reference/DemoRoomOrderDao.java"));
            String mapperTemplate = Files.readString(Paths.get("reference/DemoRoomOrderMapper.xml"));


            String webapiTemplate = Files.readString(Paths.get("reference/DemoRoomOrderAPI.js"));
            String webrouteTemplate = Files.readString(Paths.get("reference/DemoRoomOrderRoute.js"));
            String webpageTemplate = Files.readString(Paths.get("reference/DemoRoomOrder.vue"));

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
                   """.formatted(sql,sqlToIR,uriPath,busiName,busiChnName,basePackage,controllerTemplate,ServiceInterfaceTemplate,ServiceTemplate,daoTemplate,mapperTemplate,webapiTemplate,webrouteTemplate,webpageTemplate);
            String files = coder.callForString(systemPromot , userPrompt);

            String javaBase = "C:\\Users\\Administrator\\IdeaProjects\\smarcatering-srv\\src\\main\\java\\com\\suypower\\inteCater\\foodBeverages";
            String resourceBase = "C:\\Users\\Administrator\\IdeaProjects\\smarcatering-srv\\src\\main\\resources\\mapper\\inteCater\\foodBeverages";
            String vueBase = "C:\\Users\\Administrator\\IdeaProjects\\srvAssurance\\src";
            codeWriter.writeFiles(codeWriter.split(files),javaBase,resourceBase,vueBase,uriPath,busiName,busiChnName);

            return "代码已写入";
        } catch (IOException | JSQLParserException e) {
            e.printStackTrace();
        }
        return "默认代码生成结果";
    }
}
