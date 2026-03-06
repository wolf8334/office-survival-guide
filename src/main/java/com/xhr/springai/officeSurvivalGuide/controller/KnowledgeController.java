package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController()
@RequestMapping("/knowledges")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    @PostMapping("/refresh")
    public Result<CommonData> refreshKnowledge() {
        log.info("刷新向量知识库");
        knowledgeService.refreshKnowledgeBase();
        return Result.success("","向量库刷新完毕");
    }

    @PostMapping("/addKnowledge")
    public Result<CommonData> addKnowledgeBase(@RequestBody Map<String, String> knowledgeBase) {
        log.info("新增专家知识库，内容是 " +  knowledgeBase);
        knowledgeService.addKnowledgeBase(knowledgeBase);
        return Result.success("","专家知识添加完毕");
    }

    @PostMapping("/acquireKeyword")
    public Result<CommonData> acquireKeyword() {
        log.info("更新专家库的关键字 ");
        knowledgeService.acquireKeyword();
        return Result.success("","专家关键字更新完毕");
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        log.info("查询专家库关键字 ");
        return Result.success(knowledgeService.list());
    }
}