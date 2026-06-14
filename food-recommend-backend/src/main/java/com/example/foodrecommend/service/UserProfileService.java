package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.SceneContextDTO;
import com.example.foodrecommend.dto.TagInputDTO;
import com.example.foodrecommend.dto.UserProfileDTO;

/**
 * Agent 2: 用户画像服务
 * 将标签面板输入 + 场景分析结果 → 结构化用户画像 + 推荐查询文本
 * 纯 Java 转换，无 AI 调用
 */
public interface UserProfileService {
    /**
     * 根据标签 + 场景构建用户画像
     */
    UserProfileDTO buildProfile(TagInputDTO tags, SceneContextDTO sceneContext);

    /**
     * 根据用户画像构建向量检索查询文本
     */
    String buildQueryText(UserProfileDTO profile);
}
