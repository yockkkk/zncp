package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.config.FeedbackBoostProperties;
import com.example.foodrecommend.config.QdrantConfig;
import com.example.foodrecommend.service.EmbeddingService;
import com.example.foodrecommend.service.RecommendationHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
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
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Override
    public void indexAdoption(Long recordId, String queryText, List<Long> adoptedDishIds, Long waiterId) {
        if (!props.isEnabled()) return;
        // 实际写入在 Task 5 实现
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
}
