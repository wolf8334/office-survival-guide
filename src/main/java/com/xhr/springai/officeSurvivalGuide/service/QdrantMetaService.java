package com.xhr.springai.officeSurvivalGuide.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class QdrantMetaService {

    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collection;

    @Value("${spring.ai.vectorstore.qdrant.api-key}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.qdrant.use-tls}")
    private String usetls;

    @Value("${qdrant.rest-port:6333}")
    private int qdrantRestPort;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<String> getDistinctFilenames() {
        Set<String> filenames = new LinkedHashSet<>();
        String nextOffset = null;

        do {
            String http = "http://";
            if ("true".equalsIgnoreCase(usetls)){
                http = "https://";
            }

            String url = http + qdrantHost + ":" + qdrantRestPort + "/collections/" + collection + "/points/scroll";

            Map<String, Object> body = new HashMap<>();
            body.put("limit", 10000);
            body.put("with_vector", false);
            body.put("with_payload", Map.of("include", List.of("filename")));
            if (nextOffset != null) {
                body.put("offset", nextOffset);
            }

            Map<?, ?> response = restTemplate.postForObject(url, buildRequest(body), Map.class);

            Map<?, ?> result = null;
            if (response != null) {
                result = (Map<?, ?>) response.get("result");
            }
            List<Map<?, ?>> points = (List<Map<?, ?>>) result.get("points");

            for (Map<?, ?> point : points) {
                Map<?, ?> payload = (Map<?, ?>) point.get("payload");
                if (payload != null) {
                    Object fn = payload.get("filename");
                    if (fn != null) filenames.add(fn.toString());
                }
            }

            nextOffset = (String) result.get("next_page_offset");

        } while (nextOffset != null);

        return new ArrayList<>(filenames);
    }

    private HttpEntity<Map<String, Object>> buildRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);
        return new HttpEntity<>(body, headers);
    }
}
