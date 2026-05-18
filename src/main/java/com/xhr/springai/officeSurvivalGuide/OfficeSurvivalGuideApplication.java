package com.xhr.springai.officeSurvivalGuide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class OfficeSurvivalGuideApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfficeSurvivalGuideApplication.class, args);
    }
}
