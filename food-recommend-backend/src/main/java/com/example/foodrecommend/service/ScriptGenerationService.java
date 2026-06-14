package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.DishScriptDTO;
import com.example.foodrecommend.dto.RecommendDishDTO;
import com.example.foodrecommend.dto.ScriptResultDTO;
import com.example.foodrecommend.dto.UserProfileDTO;

import java.util.List;

/**
 * Agent 5: 话术生成服务
 * LLM 为服务员生成自然的推荐话术
 */
public interface ScriptGenerationService {
    /**
     * 根据用户画像和推荐结果生成话术
     * @param profile 用户画像
     * @param recommendations 排序后的推荐菜品
     * @return 话术结果（开场白 + 每道菜的话术）
     */
    ScriptResultDTO generateScripts(UserProfileDTO profile, List<RecommendDishDTO> recommendations);
}
