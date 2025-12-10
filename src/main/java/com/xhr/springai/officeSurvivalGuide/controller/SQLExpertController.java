package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.bean.CommonData;
import com.xhr.springai.officeSurvivalGuide.bean.Result;
import com.xhr.springai.officeSurvivalGuide.service.SQLExpertService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController()
@RequestMapping("/api/sqlexpert")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SQLExpertController {

    private static final Logger log = LoggerFactory.getLogger(SQLExpertController.class);

    private final SQLExpertService service;

    @PostMapping("/writeSQL")
    public Result<CommonData> writeSQL(@RequestBody Map<String, String> requirements) {
        String userRequirement = requirements.get("userRequirement");

        log.info("用户输入是 {}",userRequirement);
        return service.writeSomeSQL(userRequirement);
    }
}
