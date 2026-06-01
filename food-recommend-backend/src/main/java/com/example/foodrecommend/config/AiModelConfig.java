package com.example.foodrecommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiModelConfig {

    private ModelProperties vision = new ModelProperties();
    private ModelProperties embedding = new ModelProperties();
    private ModelProperties rerank = new ModelProperties();

    @Data
    public static class ModelProperties {
        private String apiKey;
        private String baseUrl;
        private String model;
        private int dimensions = 1536;
        private int maxTokens = 1500;
    }
}
