package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.dto.RerankResultDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.service.AiRerankService;
import com.example.foodrecommend.service.RecommendationRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 4 实现: 推荐排序
 * 委托给现有的 AiRerankService 进行 LLM 重排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationRankingServiceImpl implements RecommendationRankingService {

    private final AiRerankService aiRerankService;

    @Override
    public RerankResultDTO rank(UserProfileDTO profile, List<Dish> candidateDishes) {
        log.info("Agent4-推荐排序: 对 {} 条候选菜品进行 LLM 重排序", candidateDishes.size());
        RerankResultDTO result = aiRerankService.rerank(profile, candidateDishes);
        log.info("Agent4-推荐排序完成: 排序 {} 条", result.getRecommendations().size());
        return result;
    }
}
