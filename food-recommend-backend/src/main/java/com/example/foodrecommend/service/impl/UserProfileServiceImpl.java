package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.dto.SceneContextDTO;
import com.example.foodrecommend.dto.TagInputDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 2 实现: 用户画像构建
 * 纯业务逻辑，将标签输入映射到结构化画像
 */
@Slf4j
@Service
public class UserProfileServiceImpl implements UserProfileService {

    @Override
    public UserProfileDTO buildProfile(TagInputDTO tags, SceneContextDTO sceneContext) {
        UserProfileDTO profile = new UserProfileDTO();

        // 人数：优先使用标签，其次场景分析
        if (tags.getPeopleCount() != null) {
            profile.setPeopleCount(parsePeopleCount(tags.getPeopleCount()));
        } else if (sceneContext != null && sceneContext.getEstimatedPeopleCount() != null) {
            profile.setPeopleCount(sceneContext.getEstimatedPeopleCount());
        }

        // 用餐场景
        String scene = tags.getDiningScene();
        if (scene == null && sceneContext != null) {
            scene = sceneContext.getAtmosphere();
        }
        profile.setDiningScene(scene);

        // 消费水平：从预算标签映射
        profile.setEstimatedConsumptionLevel(mapBudgetToLevel(tags.getBudgetLevel()));

        // 偏好
        List<String> prefs = new ArrayList<>();
        if (tags.getTastePreferences() != null) {
            prefs.addAll(tags.getTastePreferences());
        }
        if (tags.getDietaryRestriction() != null && !"无".equals(tags.getDietaryRestriction())) {
            prefs.add(tags.getDietaryRestriction());
        }
        if (tags.getMealTime() != null) {
            prefs.add(tags.getMealTime());
        }
        if (prefs.isEmpty()) {
            prefs.add("营养均衡");
        }
        profile.setPossiblePreferences(prefs);

        // 健康目标
        profile.setHealthGoal(mapHealthGoal(tags.getDietaryRestriction()));

        // 推荐关键词
        List<String> keywords = new ArrayList<>();
        if (tags.getPeopleCount() != null) keywords.add(mapPeopleKeyword(tags.getPeopleCount()));
        if (tags.getDiningScene() != null) keywords.add(tags.getDiningScene());
        if (tags.getMealTime() != null) keywords.add(tags.getMealTime());
        profile.setRecommendationKeywords(keywords);

        // 来源标记
        profile.setClothingStyle(null);
        profile.setAgeRange(null);

        log.info("Agent2-用户画像构建完成: peopleCount={}, scene={}, prefs={}",
                profile.getPeopleCount(), profile.getDiningScene(), profile.getPossiblePreferences());
        return profile;
    }

    @Override
    public String buildQueryText(UserProfileDTO profile) {
        StringBuilder sb = new StringBuilder();
        if (profile.getPeopleCount() != null) {
            sb.append("当前有").append(profile.getPeopleCount()).append("人用餐，");
        }
        if (profile.getAgeRange() != null) {
            sb.append("用户年龄段约").append(profile.getAgeRange()).append("，");
        }
        if (profile.getDiningScene() != null) {
            sb.append("场景为").append(profile.getDiningScene()).append("，");
        }
        if (profile.getEstimatedConsumptionLevel() != null) {
            sb.append("消费能力").append(profile.getEstimatedConsumptionLevel()).append("，");
        }
        if (profile.getPossiblePreferences() != null && !profile.getPossiblePreferences().isEmpty()) {
            sb.append("偏好").append(String.join("、", profile.getPossiblePreferences())).append("，");
        }
        if (profile.getHealthGoal() != null) {
            sb.append("健康目标为").append(profile.getHealthGoal()).append("，");
        }
        sb.append("适合推荐价格合理、营养搭配合适、符合当前场景的菜品。");
        return sb.toString();
    }

    private Integer parsePeopleCount(String count) {
        return switch (count) {
            case "1" -> 1;
            case "2" -> 2;
            case "3-4" -> 3;
            case "5+" -> 5;
            default -> null;
        };
    }

    private String mapPeopleKeyword(String count) {
        return switch (count) {
            case "1" -> "单人餐";
            case "2" -> "两人餐";
            case "3-4" -> "多人餐";
            case "5+" -> "聚餐";
            default -> "";
        };
    }

    private String mapBudgetToLevel(String budget) {
        return switch (budget) {
            case "实惠" -> "低";
            case "中等" -> "中等";
            case "高端" -> "高";
            default -> "中等";
        };
    }

    private String mapHealthGoal(String dietary) {
        if (dietary == null) return "日常均衡饮食";
        return switch (dietary) {
            case "低脂" -> "减脂减重";
            case "高蛋白" -> "增肌塑形";
            case "素食" -> "健康素食";
            default -> "日常均衡饮食";
        };
    }
}
