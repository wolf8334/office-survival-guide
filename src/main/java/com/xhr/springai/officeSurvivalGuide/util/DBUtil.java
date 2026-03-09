package com.xhr.springai.officeSurvivalGuide.util;

import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DBUtil {

    private final JdbcTemplate pg;

    @Qualifier("tidbJdbcTemplate")
    private final JdbcTemplate mysql;

    private final JSONUtil json;

    public String getDDL(String tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return "";
        } else {
            String getDDL = "SHOW CREATE TABLE ";
            return Arrays.stream(tableNames.split(",")).map(
                    tableName -> {
                        Map<String, Object> rs = mysql.queryForMap(getDDL +  tableName);
                        // 获取第二列 "Create Table" 的内容
                        return rs.get("Create Table").toString();
                    }
            ).distinct().collect(Collectors.joining("\n\n"));
        }
    }

    public String getDatas(String tableNames){
        if (tableNames == null || tableNames.isEmpty()) {
            return "";
        } else {
            return Arrays.stream(tableNames.split(",")).map(
                    tableName -> {
                        List<Map<String, Object>> list = mysql.queryForList("SELECT * FROM " +  tableName + " LIMIT 5");
                        String data = json.parseString(list);
                        return tableName + "样例数据 \n" + data;
                    }
            ).distinct().collect(Collectors.joining("\n\n"));
        }
    }
}
