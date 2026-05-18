package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.service.LLMRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/osg")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LLMRequestController {

    private final LLMRequestService llmRequestService;

    @GetMapping("/llm/logs")
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return Map.of(
                "data", llmRequestService.list(page, size),
                "total", llmRequestService.count(),
                "page", page,
                "size", size
        );
    }
}
