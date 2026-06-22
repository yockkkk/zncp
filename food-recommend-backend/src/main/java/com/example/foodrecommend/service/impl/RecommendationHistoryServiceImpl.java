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
        // 实际查询在 Task 4 实现
        return Collections.emptyMap();
    }
}
