package com.xhr.springai.officeSurvivalGuide.bean;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HierarchyNode {

    String id;
    String title;
    int level;                        // 0=章 1=节 2=小节
    StringBuilder content = new StringBuilder();
    String parentId;
    List<String> childrenIds = new ArrayList<>();
    List<String> hierarchyPath = new ArrayList<>();

    public HierarchyNode(String id, String title, int level, String parentId, List<String> path) {
        this.id = id;
        this.title = title;
        this.level = level;
        this.parentId = parentId;
        this.hierarchyPath = new ArrayList<>(path);
    }
}
