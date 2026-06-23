package com.example.foodrecommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "recommend.feedback-boost")
public class FeedbackBoostProperties {
    private boolean enabled = true;
    private String collectionName = "recommendation_history";
    private int minSamples = 3;
    private int topKSimilar = 20;
    private double similarityThreshold = 0.75;
    private double weight = 0.15;
    private int boostCap = 5;
}
