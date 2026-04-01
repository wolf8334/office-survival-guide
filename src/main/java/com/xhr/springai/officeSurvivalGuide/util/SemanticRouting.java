package com.xhr.springai.officeSurvivalGuide.util;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.config.IntentClassification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供意图分拣/语义路由功能
 * */
@Service
@RequiredArgsConstructor
public class SemanticRouting {

    private final LLMUtil llm;
    private final JSONUtil json;

    /**
     * 分拣用户输入的问题，拆分成系统支持的功能
     * */
    public IntentClassification routeSemantic(String requirment) {
        String prompt = """
               你是一个意图识别器。请分析用户的问题，并按照枚举输出相关分类标签，使用JSON返回。
               
               answer_style表示回答方式，枚举如下。
               summary: 总体情况
               list: 清单
               table: 表格
               brief: 简短回答
               other: 其他
               
               intent表示用户的主意图，枚举如下。
               progress_status: 进展 状态
               count_stat: 数量 统计
               reason_analysis: 原因 为什么
               risk_issue: 问题 卡点
               owner_responsibility: 负责人 归属
               plan_schedule: 计划 时间节点
               solution_action: 措施 方案
               definition_explain: 概念 解释
               other: 其他
               
               target_type表示对象类型，枚举如下。  
               project_progress: 项目进展
               project_analyse: 项目分析
               project_plan: 项目计划
               usually: 综合
               menu: 菜单相关
               staff: 员工相关
               other: 其他
               
               time_scope表示时间范围，枚举如下。
               days: 一周以内
               weeks: 一月以内
               mohths: 一季度以内
               quarters: 一年以内
               years: 数年
               other: 其他
               
               规则：
                - 只输出标签和对应的标签枚举，使用JSON格式，不能确定的值就用空字符串，不要任何解释。
                - answer_style、intent、target_type、time_scope如果无法判断，则返回other
                - origin_message是用户发给你的消息，保留输入不要动。
               JSON格式如下
                {
                  "intent": "",
                  "target_type": "",
                  "target_name": "",
                  "time_scope": "",
                  "department": "",
                  "person": "",
                  "org": "",
                  "query_rewrite": "",
                  "answer_style": "",
                  "origin_message": "%s"
                }
               """.formatted(json.parseComma(requirment));
        Result<CommonData> rs = llm.call(requirment, prompt);
        return json.parseString(rs.data().translate(),IntentClassification.class);
    }
}
