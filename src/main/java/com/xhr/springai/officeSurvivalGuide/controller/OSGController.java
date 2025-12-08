package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.service.OSGService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController()
@RequestMapping("/api/office")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OSGController {

    private static final Logger log = LoggerFactory.getLogger(OSGController.class);

    private final OSGService service;

    @PostMapping("/rag")
    public Result<CommonData> refreshKnowledge(@RequestBody Map<String, String> requirements) {
        String userRequirement = requirements.get("userRequirement");

        log.info("用户输入是 {} ", userRequirement);
        return service.sayItBetter(userRequirement);
    }

    @PostMapping("/pretty")
    public Result<CommonData> makeItPretty(@RequestBody Map<String, String> requirements) {
        String userRequirement = requirements.get("userRequirement");

        log.info("用户输入是 {} ", userRequirement);
        return service.makeItPretty(userRequirement);
    }
}
