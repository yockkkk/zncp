package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.config.FeedbackBoostProperties;
import com.example.foodrecommend.config.QdrantConfig;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.entity.FeedbackIndexDlq;
import com.example.foodrecommend.mapper.FeedbackIndexDlqMapper;
import com.example.foodrecommend.service.EmbeddingService;
import com.example.foodrecommend.service.RecommendationHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationHistoryServiceImpl implements RecommendationHistoryService {

    private final FeedbackBoostProperties props;
    private final QdrantConfig qdrantConfig;
    private final AiModelConfig aiModelConfig;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final FeedbackIndexDlqMapper dlqMapper;

    @Async
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500))
    @Override
    public void indexAdoption(Long recordId, String queryText, List<Long> adoptedDishIds, Long waiterId) {
        if (!props.isEnabled()) return;
        if (adoptedDishIds == null || adoptedDishIds.isEmpty()) return;
        try {
            List<Float> vec = embeddingService.getEmbedding(queryText);
            if (vec == null || vec.isEmpty()) return;

            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("adoptedDishIds", adoptedDishIds);
            payload.put("waiterId", waiterId);
            payload.put("createTime", System.currentTimeMillis());

            Map<String, Object> point = new java.util.HashMap<>();
            point.put("id", recordId);
            point.put("vector", vec);
            point.put("payload", payload);

            Map<String, Object> body = Map.of("points", List.of(point));
            String url = "http://" + qdrantConfig.getHost() + ":" + qdrantConfig.getPort()
                    + "/collections/" + props.getCollectionName() + "/points";

            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url)
                    .put(okhttp3.RequestBody.create(objectMapper.writeValueAsString(body),
                            okhttp3.MediaType.parse("application/json")))
                    .build();
            try (okhttp3.Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw new java.io.IOException("qdrant upsert failed: " + resp.code());
                }
                log.info("index.adoption recordId={} dishIds={}", recordId, adoptedDishIds.size());
            }
        } catch (Exception e) {
            throw new RuntimeException(e); // 让 @Retryable 接管
        }
    }

    @Recover
    public void recoverIndexAdoption(Exception e, Long recordId, String queryText,
                                     List<Long> adoptedDishIds, Long waiterId) {
        log.warn("index.adoption.dlq recordId={} err={}", recordId, e.getMessage());
        FeedbackIndexDlq row = new FeedbackIndexDlq();
        row.setRecordId(recordId);
        row.setError(String.valueOf(e.getMessage()));
        row.setRetryCount(2);
        dlqMapper.insert(row);
    }

    @Override
    public Map<Long, Integer> lookupBoost(String queryText) {
        if (!props.isEnabled()) return Collections.emptyMap();
        try {
            List<Float> vec = embeddingService.getEmbedding(queryText);
            if (vec == null || vec.isEmpty()) return Collections.emptyMap();

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("vector", vec);
            body.put("limit", props.getTopKSimilar());
            body.put("with_payload", true);
            body.put("score_threshold", props.getSimilarityThreshold());

            String url = "http://" + qdrantConfig.getHost() + ":" + qdrantConfig.getPort()
                    + "/collections/" + props.getCollectionName() + "/points/search";

            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url)
                    .post(okhttp3.RequestBody.create(objectMapper.writeValueAsString(body),
                            okhttp3.MediaType.parse("application/json")))
                    .build();

            try (okhttp3.Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    log.warn("boost.lookup.miss reason=qdrant_error code={}", resp.code());
                    return Collections.emptyMap();
                }
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(resp.body().string());
                com.fasterxml.jackson.databind.JsonNode results = root.path("result");
                if (!results.isArray() || results.size() == 0) {
                    log.info("boost.lookup.miss reason=no_similar");
                    return Collections.emptyMap();
                }
                Map<Long, Integer> agg = new java.util.HashMap<>();
                int sampleCount = 0;
                for (com.fasterxml.jackson.databind.JsonNode node : results) {
                    double score = node.path("score").asDouble(0.0);
                    if (score < props.getSimilarityThreshold()) continue;
                    com.fasterxml.jackson.databind.JsonNode ids = node.path("payload").path("adoptedDishIds");
                    if (!ids.isArray() || ids.size() == 0) continue;
                    sampleCount++;
                    for (com.fasterxml.jackson.databind.JsonNode id : ids) {
                        long dishId = id.asLong();
                        agg.merge(dishId, 1, Integer::sum);
                    }
                }
                if (sampleCount < props.getMinSamples()) {
                    log.info("boost.lookup.miss reason=below_min_samples samples={}", sampleCount);
                    return Collections.emptyMap();
                }
                log.info("boost.lookup.hit samples={} boostedCount={}", sampleCount, agg.size());
                return agg;
            }
        } catch (Exception e) {
            log.warn("boost.lookup.miss reason=exception msg={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @jakarta.annotation.PostConstruct
    public void initOnBoot() {
        if (!props.isEnabled()) return;
        try { initHistoryCollection(); } catch (Exception e) {
            log.warn("history collection init failed: {}", e.getMessage());
        }
    }

    @Override
    public void initHistoryCollection() {
        String url = "http://" + qdrantConfig.getHost() + ":" + qdrantConfig.getPort()
                + "/collections/" + props.getCollectionName();
        try {
            okhttp3.Request get = new okhttp3.Request.Builder().url(url).get().build();
            okhttp3.Call getCall = httpClient.newCall(get);
            if (getCall == null) return;
            try (okhttp3.Response resp = getCall.execute()) {
                if (resp.isSuccessful()) {
                    log.info("history collection {} 已存在", props.getCollectionName());
                    return;
                }
            }
            // 注入 AiModelConfig 拿 dimensions
            int dims = aiModelConfig.getEmbedding().getDimensions();
            Map<String, Object> body = Map.of("vectors", Map.of("size", dims, "distance", "Cosine"));
            okhttp3.Request put = new okhttp3.Request.Builder()
                    .url(url)
                    .put(okhttp3.RequestBody.create(objectMapper.writeValueAsString(body),
                            okhttp3.MediaType.parse("application/json")))
                    .build();
            okhttp3.Call putCall = httpClient.newCall(put);
            if (putCall == null) return;
            try (okhttp3.Response resp = putCall.execute()) {
                if (resp.isSuccessful()) {
                    log.info("history collection {} 创建成功 dim={}", props.getCollectionName(), dims);
                } else {
                    log.warn("history collection 创建失败 code={}", resp.code());
                }
            }
        } catch (Exception e) {
            log.warn("history collection init exception: {}", e.getMessage());
        }
    }
}
