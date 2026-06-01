package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final AiModelConfig aiModelConfig;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Override
    public List<Float> getEmbedding(String text) {
        AiModelConfig.ModelProperties config = aiModelConfig.getEmbedding();

        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", config.getModel());
            requestMap.put("input", text);

            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/embeddings")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(requestMap),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("Embedding API 调用失败: " + response.code());
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode embeddingArray = root.path("data").get(0).path("embedding");

                List<Float> embedding = new ArrayList<>();
                for (JsonNode node : embeddingArray) {
                    embedding.add(node.floatValue());
                }
                return embedding;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding API 调用异常", e);
            throw new BusinessException("Embedding 生成失败: " + e.getMessage());
        }
    }
}
