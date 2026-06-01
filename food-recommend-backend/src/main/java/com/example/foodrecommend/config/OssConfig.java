package com.example.foodrecommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oss")
public class OssConfig {
    private boolean enabled = false;
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
}
