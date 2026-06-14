package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.SceneContextDTO;

/**
 * Agent 1: 场景感知服务
 * 分析用餐环境照片（桌型、时段、氛围、人数估计），不识别面孔
 */
public interface ScenePerceptionService {
    /**
     * 分析场景照片，返回场景上下文
     * @param sceneImageUrl 场景图片URL
     * @return 场景分析结果，若无照片则返回 null
     */
    SceneContextDTO analyzeScene(String sceneImageUrl);
}
