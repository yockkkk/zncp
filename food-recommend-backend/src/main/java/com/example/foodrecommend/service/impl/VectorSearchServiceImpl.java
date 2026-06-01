package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.config.QdrantConfig;
import com.example.foodrecommend.dto.DishVectorDTO;
import com.example.foodrecommend.service.EmbeddingService;
import com.example.foodrecommend.service.VectorSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchServiceImpl implements VectorSearchService {

    private final QdrantConfig qdrantConfig;
    private final AiModelConfig aiModelConfig;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    private String baseUrl;

    @PostConstruct
    public void init() {
        baseUrl = "http://" + qdrantConfig.getHost() + ":" + qdrantConfig.getPort();
    }

    @Override
    public void initCollection() {
        String collectionName = qdrantConfig.getCollectionName();
        int dimensions = aiModelConfig.getEmbedding().getDimensions();

        try {
            Request checkRequest = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName)
                    .get()
                    .build();

            try (Response checkResponse = httpClient.newCall(checkRequest).execute()) {
                if (checkResponse.isSuccessful()) {
                    log.info("Qdrant collection {} 已存在", collectionName);
                    return;
                }
            }

            Map<String, Object> body = Map.of(
                    "vectors", Map.of("size", dimensions, "distance", "Cosine")
            );

            Request createRequest = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName)
                    .put(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response createResponse = httpClient.newCall(createRequest).execute()) {
                if (createResponse.isSuccessful()) {
                    log.info("Qdrant collection {} 创建成功, dimensions={}", collectionName, dimensions);
                } else {
                    String err = createResponse.body() != null ? createResponse.body().string() : "";
                    log.warn("Qdrant collection 创建返回 {}: {}", createResponse.code(), err);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Qdrant collection 初始化失败", e);
            throw new BusinessException("Qdrant 初始化失败，请确认服务已启动: " + e.getMessage());
        }
    }

    @Override
    public void upsertDishVector(Long dishId, List<Float> vector, DishVectorDTO payload) {
        String collectionName = qdrantConfig.getCollectionName();

        try {
            Map<String, Object> point = new HashMap<>();
            point.put("id", dishId);
            point.put("vector", vector);
            point.put("payload", payload);

            Map<String, Object> body = Map.of("points", List.of(point));

            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points")
                    .put(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new BusinessException("Qdrant upsert 失败: " + response.code());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Qdrant upsert 异常, dishId={}", dishId, e);
            throw new BusinessException("向量写入失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteDishVector(Long dishId) {
        String collectionName = qdrantConfig.getCollectionName();

        try {
            Map<String, Object> body = Map.of("points", List.of(dishId));

            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points/delete")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new BusinessException("Qdrant 删除向量失败: " + response.code());
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Qdrant 删除向量异常, dishId={}", dishId, e);
            throw new BusinessException("向量删除失败: " + e.getMessage());
        }
    }

    @Override
    public List<Long> searchSimilarDishes(String queryText, int topK) {
        List<Float> queryVector = embeddingService.getEmbedding(queryText);
        String collectionName = qdrantConfig.getCollectionName();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", queryVector);
            body.put("limit", topK);
            body.put("with_payload", true);
            body.put("score_threshold", 0.3);

            Request request = new Request.Builder()
                    .url(baseUrl + "/collections/" + collectionName + "/points/search")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("Qdrant 搜索失败: " + response.code());
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode resultArray = root.path("result");

                List<Long> dishIds = new ArrayList<>();
                for (JsonNode node : resultArray) {
                    double score = node.path("score").asDouble(0.0);
                    long id = node.path("id").asLong();
                    if (score > 0.3) {
                        dishIds.add(id);
                    }
                }
                return dishIds;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Qdrant 搜索异常", e);
            throw new BusinessException("向量搜索失败: " + e.getMessage());
        }
    }
}
