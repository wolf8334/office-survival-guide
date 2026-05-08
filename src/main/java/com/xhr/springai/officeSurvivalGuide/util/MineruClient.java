package com.xhr.springai.officeSurvivalGuide.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class MineruClient {

    @Value("${custom.mineru-url}")
    private String mineruUrl;

    @Value("${custom.mineru-backend}")
    private String backend;

    @Value("${custom.mineru-return_md}")
    private String returnmd;

    @Value("${custom.return_content_list}")
    private String return_content_list;


    private final WebClient.Builder webClient;

    public String call(MultipartFile file){
        try {
            File tempFile = File.createTempFile("tmp", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new FileSystemResource(tempFile));
            body.add("return_md", returnmd);
            body.add("backend", backend);
            body.add("return_content_list", return_content_list);

            tempFile.deleteOnExit();

            return webClient.build().post()
                    .uri(mineruUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)  // 换成 multipart
                    .bodyValue(body)                              // body 是 MultiValueMap
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("MinerU 状态码: {}", response.statusCode());
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("MinerU 错误响应: {}", errorBody);
                                    return Mono.error(new RuntimeException(errorBody));
                                });
                    })
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("MinerU错误: {}", errorBody);
                                        return Mono.error(new RuntimeException(errorBody));
                                    })
                    )
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception _) {
        }
        return "";
    }
}
