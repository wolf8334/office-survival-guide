package com.xhr.springai.officeSurvivalGuide.bean;

import lombok.Data;

import java.util.List;

@Data
public class SqlIr {

    private List<String> where;

    private List<ColumnInfo> columns;

    @Data
    public static class ColumnInfo {
        private String column;
        private String alias;
    }
}
