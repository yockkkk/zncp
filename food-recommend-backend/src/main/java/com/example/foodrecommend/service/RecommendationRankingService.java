package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.RerankResultDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;

import java.util.List;

/**
 * Agent 4: 推荐排序服务
 * LLM 对候选菜品进行重排序，输出排名 + 评分 + 理由
 */
public interface RecommendationRankingService {
    /**
     * 对候选菜品进行智能重排序
     * @param profile 用户画像
     * @param candidateDishes 候选菜品列表
     * @return 排序结果（含 summary + ranked dishes）
     */
    RerankResultDTO rank(UserProfileDTO profile, List<Dish> candidateDishes);
}
