package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.service.CodeService;
import com.xhr.springai.officeSurvivalGuide.service.HQService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController()
@RequestMapping("/hqdmx")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HQController {

    private static final Logger log = LoggerFactory.getLogger(HQController.class);

    private final HQService service;
    private final CodeService code;

    @Operation(method = "POST", description = "根据后勤业务知识，回答用户问题")
    @PostMapping(value = "/acknowledge", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> acknowledge(@RequestBody Map<String, String> requirements) {
        String userRequirement = requirements.get("userRequirement");
        log.info("用户输入是 {} ", userRequirement);

        return service.acknowledge(userRequirement);
    }


    @PostMapping("/summarize")
    public String summarize(@RequestParam("file") MultipartFile file) {
        return service.processFile(file);
    }

    @PostMapping("/vectorize")
    public String vectorize(@RequestParam("file") MultipartFile file) {
        return service.vectorize(file);
    }

    @PostMapping("/code")
    public String writeCode(@RequestBody Map<String, String> param) {
        return code.writeCode(param);
    }
}
