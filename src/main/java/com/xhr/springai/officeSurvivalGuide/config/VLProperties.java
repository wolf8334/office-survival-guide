package com.xhr.springai.officeSurvivalGuide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "custom.vl")
public class VLProperties extends BaseAiProperties {
}
