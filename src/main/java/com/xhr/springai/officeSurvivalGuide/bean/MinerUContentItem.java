package com.xhr.springai.officeSurvivalGuide.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinerUContentItem {
    private String type;       // text / table / image 等
    private String text;       // 文字内容
    private Integer text_level; // 标题级别，1=一级标题，null=正文
    private Integer page_idx;  // 页码，从 0 开始
    private List<String> table_caption;
    private List<String> table_footnote;
    private String table_body;
    private String img_path;
}
