package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;

import java.util.List;

/**
 * Agent 3: 菜品匹配服务
 * Embedding → Qdrant 向量检索 → MySQL 完整数据 → 规则过滤
 */
public interface DishMatchingService {
    /**
     * 根据查询文本和用户画像匹配菜品
     * @param queryText 向量检索查询文本
     * @param profile 用户画像（用于规则过滤）
     * @param topK 向量检索返回数量
     * @return 过滤后的候选菜品列表
     */
    List<Dish> matchDishes(String queryText, UserProfileDTO profile, int topK);
}
