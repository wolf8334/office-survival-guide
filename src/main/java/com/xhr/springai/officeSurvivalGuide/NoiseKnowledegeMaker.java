package com.xhr.springai.officeSurvivalGuide;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

public class NoiseKnowledegeMaker {
    public static void main(String[] args) {
        String fileName = "expert_rules_10000.sql";
        int totalRecords = 10000;
        Random random = new Random();

        // 核心测试案例：用于演示 Rerank 的“反转”效果
        String[][] demoCases = {
                {"离线状态机同步协议", "该协议通过物理介质在不联网环境下进行数据对齐，确保强一致性。"},
                {"在线实时同步协议", "基于长连接的实时数据交换机制，要求网络延迟低于 50ms。"},
                {"物理层静默降噪", "利用高密度声学棉进行空间填充，通过物理阻隔降低噪音，不耗电。"},
                {"电子主动声波控制", "通过传感器监测环境频率，发射反向相位波进行能量抵消，需电力驱动。"},
                {"原生 Bean 注入逻辑", "讨论 Spring 框架底层的依赖倒置实现，不涉及任何外部扩展包。"},
                {"外部推理插件适配", "专门为 Spring 框架开发的第三方扩展，用于连接外部的计算引擎。"}
        };

        String[] industries = {"电力调度", "轨道交通", "智慧医疗", "精密加工", "行政审批", "金融合规"};
        String[] descriptions = {
                "该规范定义了系统在极端负载下的熔断逻辑。",
                "本条目属于 V3.2 版本的标准操作程序（SOP）。",
                "用于规范化大规模生产集群的自动化监控策略。",
                "记录了针对高并发场景下的索引优化方案。"
        };

        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("BEGIN;"); // 事务开始，提升插入速度

            // 1. 插入混淆数据和陷阱
            for (int i = 1; i <= totalRecords; i++) {
                String keyword;
                String explanation;

                if (i <= 30) {
                    // 前30条使用精心准备的“陷阱”数据
                    int idx = i % demoCases.length;
                    keyword = demoCases[idx][0] + "_" + i;
                    explanation = demoCases[idx][1];
                } else {
                    // 剩下的 9970 条生成看起来很专业的“业务噪音”
                    keyword = industries[random.nextInt(industries.length)] + "_规约_" + i;
                    explanation = descriptions[random.nextInt(descriptions.length)]
                            + " 关联编号: " + (100000 + i)
                            + "。该内容用于系统基准测试，确保在高压力环境下检索的稳定性。";
                }

                // 处理 SQL 中的单引号转义
                explanation = explanation.replace("'", "''");

                writer.printf("INSERT INTO public.sys_expert_rules (keyword, explanation) VALUES ('%s', '%s');%n",
                        keyword, explanation);
            }

            writer.println("COMMIT;"); // 提交事务
            System.out.println("生成成功！文件保存在: " + fileName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
