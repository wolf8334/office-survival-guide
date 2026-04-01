package com.xhr.springai.officeSurvivalGuide.config;

import com.xhr.springai.officeSurvivalGuide.config.enums.ANSWER_STYLE;
import com.xhr.springai.officeSurvivalGuide.config.enums.INTENT;
import com.xhr.springai.officeSurvivalGuide.config.enums.TARGET_TYPE;
import com.xhr.springai.officeSurvivalGuide.config.enums.TIME_SCOPE;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class IntentClassification {

    //主意图
    public INTENT intent;

    //对象类型
    public TARGET_TYPE target_type;

    //对象名称
    public String target_name;

    //时间范围
    public TIME_SCOPE time_scope;

    //人员标识
    public String person;

    //所属单位
    public String org;

    //所属部门
    public String department;

    //标准检索语句
    public String query_rewrite;

    //回答方式
    public ANSWER_STYLE answer_style;

    //原文
    public String origin_message;
}
