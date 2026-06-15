package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.dto.GuestProfile;
import com.example.foodrecommend.dto.SceneContextDTO;
import com.example.foodrecommend.dto.TagInputDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

        // 检查是否有多人数据
        if (tags.getGuests() != null && !tags.getGuests().isEmpty()) {
            List<GuestProfile> guests = tags.getGuests();
            profile.setGuests(guests);
            profile.setPeopleCount(guests.size());

            // 用餐场景
            String scene = tags.getDiningScene();
            if (scene == null && sceneContext != null) {
                scene = sceneContext.getAtmosphere();
            }
            if (scene == null) {
                scene = "朋友聚餐";
            }
            profile.setDiningScene(scene);

            // 消费水平
            profile.setEstimatedConsumptionLevel(mapBudgetToLevel(tags.getBudgetLevel()));

            // 合并忌口与过敏等
            Set<String> avoids = new LinkedHashSet<>();
            Set<String> allergens = new LinkedHashSet<>();
            Set<String> diseases = new LinkedHashSet<>();
            Set<String> lifestyles = new LinkedHashSet<>();
            Set<String> allPrefs = new LinkedHashSet<>();

            for (GuestProfile guest : guests) {
                if (guest.getAvoidIngredients() != null) avoids.addAll(guest.getAvoidIngredients());
                if (guest.getAllergens() != null) allergens.addAll(guest.getAllergens());
                if (guest.getDiseases() != null) diseases.addAll(guest.getDiseases());
                if (guest.getDietLifestyles() != null) lifestyles.addAll(guest.getDietLifestyles());
                if (guest.getTastes() != null) allPrefs.addAll(guest.getTastes());
            }

            profile.setConsolidatedAvoids(new ArrayList<>(avoids));
            profile.setConsolidatedAllergens(new ArrayList<>(allergens));
            profile.setConsolidatedDiseases(new ArrayList<>(diseases));
            profile.setConsolidatedDietLifestyles(new ArrayList<>(lifestyles));

            // 汇总偏好
            List<String> prefs = new ArrayList<>(allPrefs);
            if (tags.getMealTime() != null) {
                prefs.add(tags.getMealTime());
            }
            if (prefs.isEmpty()) {
                prefs.add("营养均衡");
            }
            profile.setPossiblePreferences(prefs);

            // 健康目标
            profile.setHealthGoal(mapHealthGoalFromList(profile.getConsolidatedDietLifestyles()));

            // 推荐关键词
            List<String> keywords = new ArrayList<>();
            keywords.add("多人餐");
            if (tags.getDiningScene() != null) keywords.add(tags.getDiningScene());
            if (tags.getMealTime() != null) keywords.add(tags.getMealTime());
            profile.setRecommendationKeywords(keywords);

            log.info("Agent2-多人桌画像构建完成: guestsCount={}, scene={}, consolidatedAvoids={}",
                    guests.size(), profile.getDiningScene(), profile.getConsolidatedAvoids());
        } else {
            // 单人/老版本兼容模式
            if (tags.getPeopleCount() != null) {
                profile.setPeopleCount(parsePeopleCount(tags.getPeopleCount()));
            } else if (sceneContext != null && sceneContext.getEstimatedPeopleCount() != null) {
                profile.setPeopleCount(sceneContext.getEstimatedPeopleCount());
            }

            String scene = tags.getDiningScene();
            if (scene == null && sceneContext != null) {
                scene = sceneContext.getAtmosphere();
            }
            profile.setDiningScene(scene);

            profile.setEstimatedConsumptionLevel(mapBudgetToLevel(tags.getBudgetLevel()));

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

            profile.setHealthGoal(mapHealthGoal(tags.getDietaryRestriction()));

            List<String> keywords = new ArrayList<>();
            if (tags.getPeopleCount() != null) keywords.add(mapPeopleKeyword(tags.getPeopleCount()));
            if (tags.getDiningScene() != null) keywords.add(tags.getDiningScene());
            if (tags.getMealTime() != null) keywords.add(tags.getMealTime());
            profile.setRecommendationKeywords(keywords);

            profile.setConsolidatedAvoids(Collections.emptyList());
            profile.setConsolidatedAllergens(Collections.emptyList());
            profile.setConsolidatedDiseases(Collections.emptyList());
            profile.setConsolidatedDietLifestyles(Collections.emptyList());

            log.info("Agent2-单人画像构建完成: peopleCount={}, scene={}, prefs={}",
                    profile.getPeopleCount(), profile.getDiningScene(), profile.getPossiblePreferences());
        }

        profile.setClothingStyle(null);
        profile.setAgeRange(null);
        return profile;
    }

    @Override
    public String buildQueryText(UserProfileDTO profile) {
        StringBuilder sb = new StringBuilder();
        if (profile.getGuests() != null && !profile.getGuests().isEmpty()) {
            sb.append("这是一桌多人配餐需求，共").append(profile.getPeopleCount()).append("位顾客用餐。");
            if (profile.getDiningScene() != null) {
                sb.append("用餐场景：").append(profile.getDiningScene()).append("，");
            }
            if (profile.getEstimatedConsumptionLevel() != null) {
                sb.append("消费预算水平：").append(profile.getEstimatedConsumptionLevel()).append("。");
            }

            sb.append("整桌禁止成分与忌口包括：");
            List<String> allRestrictions = new ArrayList<>();
            if (profile.getConsolidatedAvoids() != null) allRestrictions.addAll(profile.getConsolidatedAvoids());
            if (profile.getConsolidatedAllergens() != null) allRestrictions.addAll(profile.getConsolidatedAllergens());
            if (profile.getConsolidatedDiseases() != null) allRestrictions.addAll(profile.getConsolidatedDiseases());
            if (profile.getConsolidatedDietLifestyles() != null) allRestrictions.addAll(profile.getConsolidatedDietLifestyles());

            if (!allRestrictions.isEmpty()) {
                sb.append("需避开【").append(String.join("、", allRestrictions)).append("】；");
            } else {
                sb.append("无特殊避忌；");
            }

            sb.append("每个顾客的偏好详情如下：");
            for (GuestProfile guest : profile.getGuests()) {
                sb.append("[").append(guest.getName()).append("]要求：");
                if (guest.getTastes() != null && !guest.getTastes().isEmpty()) {
                    sb.append("喜欢").append(String.join("、", guest.getTastes())).append("，");
                }
                List<String> guestAvoids = new ArrayList<>();
                if (guest.getAvoidIngredients() != null) guestAvoids.addAll(guest.getAvoidIngredients());
                if (guest.getAllergens() != null) guestAvoids.addAll(guest.getAllergens());
                if (guest.getDiseases() != null) guestAvoids.addAll(guest.getDiseases());
                if (guest.getDietLifestyles() != null) guestAvoids.addAll(guest.getDietLifestyles());

                if (!guestAvoids.isEmpty()) {
                    sb.append("避开").append(String.join("、", guestAvoids)).append("；");
                } else {
                    sb.append("无禁忌；");
                }
            }
            sb.append("请推荐能覆盖所有人需求、口味平衡且营养搭配合理的菜品组合。");
        } else {
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
        }
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

    private String mapHealthGoalFromList(List<String> lifestyles) {
        if (lifestyles == null || lifestyles.isEmpty()) return "日常均衡饮食";
        if (lifestyles.contains("减脂") || lifestyles.contains("低脂")) return "减脂减重";
        if (lifestyles.contains("高蛋白")) return "增肌塑形";
        if (lifestyles.contains("素食")) return "健康素食";
        return "日常均衡饮食";
    }
}
