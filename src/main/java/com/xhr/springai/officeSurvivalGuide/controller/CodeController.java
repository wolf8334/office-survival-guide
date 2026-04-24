package com.xhr.springai.officeSurvivalGuide.controller;

import com.xhr.springai.officeSurvivalGuide.bean.GenerateCode;
import com.xhr.springai.officeSurvivalGuide.bean.GenerateResult;
import com.xhr.springai.officeSurvivalGuide.service.CodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController()
@RequestMapping("/coder")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CodeController {

    private final CodeService code;

    @PostMapping("/code")
    public String writeCode(@RequestBody Map<String, String> param) {

        return code.writeCode(param);
    }

    @PostMapping("/generate/analyze")
    public String analyze(@RequestBody Map<String, String> param) {

        return code.analyze(param);
    }

    @PostMapping("/generate/code")
    public GenerateResult writeFullCode(@RequestBody GenerateCode genCode) {

        return code.writeFullCode(genCode);
    }

    @GetMapping("/generate/download")
    public ResponseEntity<byte[]> download(@RequestParam String token) {
        byte[] zip = code.download(token);

        if (zip == null) {
            log.info("未找到 {} 对应的文件", token);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=code-" + token + ".zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    @GetMapping("/generate/downloadFile")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String token,
                                               @RequestParam String name) throws Exception {
        byte[] file = code.downloadFile(token, name);
        if (file != null) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + name)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(file);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
