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
import java.util.Map;
import java.util.HashMap;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.entity.RecommendationFeedback;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.mapper.RecommendationFeedbackMapper;
import com.example.foodrecommend.service.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 2 实现: 用户画像构建
 * 纯业务逻辑，将标签输入映射到结构化画像
 */
@Slf4j
@Service
public class UserProfileServiceImpl implements UserProfileService {

    @Autowired
    private RecommendationRecordMapper recordMapper;
    @Autowired
    private RecommendationFeedbackMapper feedbackMapper;
    @Autowired
    private DishService dishService;
    @Autowired
    private ObjectMapper objectMapper;

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
            
            List<String> currentAvoids = tags.getAvoidIngredients() != null ? new ArrayList<>(tags.getAvoidIngredients()) : new ArrayList<>();
            List<String> currentAllergens = tags.getAllergens() != null ? new ArrayList<>(tags.getAllergens()) : new ArrayList<>();
            List<String> currentDiseases = tags.getDiseases() != null ? new ArrayList<>(tags.getDiseases()) : new ArrayList<>();
            List<String> currentLifestyles = tags.getDietLifestyles() != null ? new ArrayList<>(tags.getDietLifestyles()) : new ArrayList<>();

            if (tags.getDietaryRestriction() != null && !"无".equals(tags.getDietaryRestriction())) {
                prefs.add(tags.getDietaryRestriction());
                if (!currentLifestyles.contains(tags.getDietaryRestriction())) {
                    currentLifestyles.add(tags.getDietaryRestriction());
                }
            }
            if (tags.getMealTime() != null) {
                prefs.add(tags.getMealTime());
            }

            // 长期记忆融合：如果是常规推荐且有手机号
            if (tags.getPhone() != null && !tags.getPhone().trim().isEmpty()) {
                UserProfileDTO historyProfile = getCustomerHistoryProfile(tags.getPhone().trim());
                profile.setPhone(tags.getPhone().trim());
                profile.setHistoryDescription(historyProfile.getHistoryDescription());
                profile.setHistoryTastes(historyProfile.getHistoryTastes());
                if (historyProfile.getHistoryTastes() != null) {
                    for (String taste : historyProfile.getHistoryTastes()) {
                        if (!prefs.contains(taste)) {
                            prefs.add(taste);
                        }
                    }
                }
                if (historyProfile.getConsolidatedAvoids() != null) {
                    for (String avoid : historyProfile.getConsolidatedAvoids()) {
                        if (!currentAvoids.contains(avoid)) {
                            currentAvoids.add(avoid);
                        }
                    }
                }
                if (historyProfile.getConsolidatedAllergens() != null) {
                    for (String allergen : historyProfile.getConsolidatedAllergens()) {
                        if (!currentAllergens.contains(allergen)) {
                            currentAllergens.add(allergen);
                        }
                    }
                }
                if (historyProfile.getConsolidatedDiseases() != null) {
                    for (String disease : historyProfile.getConsolidatedDiseases()) {
                        if (!currentDiseases.contains(disease)) {
                            currentDiseases.add(disease);
                        }
                    }
                }
                if (historyProfile.getConsolidatedDietLifestyles() != null) {
                    for (String lifestyle : historyProfile.getConsolidatedDietLifestyles()) {
                        if (!currentLifestyles.contains(lifestyle)) {
                            currentLifestyles.add(lifestyle);
                        }
                    }
                }
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

            profile.setConsolidatedAvoids(currentAvoids);
            profile.setConsolidatedAllergens(currentAllergens);
            profile.setConsolidatedDiseases(currentDiseases);
            profile.setConsolidatedDietLifestyles(currentLifestyles);

            log.info("Agent2-单人画像构建完成: peopleCount={}, scene={}, prefs={}",
                    profile.getPeopleCount(), profile.getDiningScene(), profile.getPossiblePreferences());
        }

        // 透传原始标签字段，方便历史记录展示
        profile.setTastePreferences(tags.getTastePreferences());
        profile.setMealTime(tags.getMealTime());

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
            if (profile.getHistoryDescription() != null && !profile.getHistoryDescription().isEmpty()) {
                sb.append("该顾客有长期记忆历史口味偏好画像（常点或喜爱）：").append(profile.getHistoryDescription()).append("，");
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

    @Override
    public UserProfileDTO getCustomerHistoryProfile(String phone) {
        UserProfileDTO profile = new UserProfileDTO();
        profile.setPhone(phone);

        List<RecommendationRecord> records = recordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecommendationRecord>()
                        .eq(RecommendationRecord::getPhone, phone)
                        .orderByDesc(RecommendationRecord::getCreateTime)
        );

        if (records == null || records.isEmpty()) {
            profile.setHistoryDescription("新顾客 (无历史消费记录)");
            profile.setHistoryTastes(Collections.emptyList());
            return profile;
        }

        int visitsCount = records.size();

        // 统计历史选过的标签及各种偏好与限制
        Set<String> pastSelectedTastes = new LinkedHashSet<>();
        Set<String> pastSelectedAvoids = new LinkedHashSet<>();
        Set<String> pastSelectedAllergens = new LinkedHashSet<>();
        Set<String> pastSelectedDiseases = new LinkedHashSet<>();
        Set<String> pastSelectedLifestyles = new LinkedHashSet<>();

        for (RecommendationRecord r : records) {
            if (r.getTagInputJson() != null) {
                try {
                    TagInputDTO tags = objectMapper.readValue(r.getTagInputJson(), TagInputDTO.class);
                    if ("multi".equals(tags.getMode()) && tags.getGuests() != null) {
                        for (GuestProfile guest : tags.getGuests()) {
                            if (guest.getAvoidIngredients() != null) pastSelectedAvoids.addAll(guest.getAvoidIngredients());
                            if (guest.getAllergens() != null) pastSelectedAllergens.addAll(guest.getAllergens());
                            if (guest.getDiseases() != null) pastSelectedDiseases.addAll(guest.getDiseases());
                            if (guest.getDietLifestyles() != null) pastSelectedLifestyles.addAll(guest.getDietLifestyles());
                            if (guest.getTastes() != null) pastSelectedTastes.addAll(guest.getTastes());
                        }
                    } else {
                        // single mode
                        if (tags.getTastePreferences() != null) {
                            pastSelectedTastes.addAll(tags.getTastePreferences());
                        }
                        if (tags.getAvoidIngredients() != null) {
                            pastSelectedAvoids.addAll(tags.getAvoidIngredients());
                        }
                        if (tags.getAllergens() != null) {
                            pastSelectedAllergens.addAll(tags.getAllergens());
                        }
                        if (tags.getDiseases() != null) {
                            pastSelectedDiseases.addAll(tags.getDiseases());
                        }
                        if (tags.getDietLifestyles() != null) {
                            pastSelectedLifestyles.addAll(tags.getDietLifestyles());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析历史标签失败", e);
                }
            }
        }

        // 统计历史采纳的菜品
        List<Long> recordIds = records.stream().map(RecommendationRecord::getId).toList();
        List<RecommendationFeedback> feedbacks = feedbackMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecommendationFeedback>()
                        .in(RecommendationFeedback::getRecordId, recordIds)
                        .isNotNull(RecommendationFeedback::getAdoptedDishId)
        );

        Map<Long, Integer> dishCounts = new HashMap<>();
        for (RecommendationFeedback fb : feedbacks) {
            if (fb.getAdoptedDishId() != null) {
                int qty = fb.getQuantity() != null ? fb.getQuantity() : 1;
                dishCounts.put(fb.getAdoptedDishId(), dishCounts.getOrDefault(fb.getAdoptedDishId(), 0) + qty);
            }
        }

        // 查询这些菜品的名字
        List<String> topDishNames = new ArrayList<>();
        int sourScore = 0;
        int numbScore = 0;
        int sweetScore = 0;
        int tenderScore = 0;

        if (!dishCounts.isEmpty()) {
            // 按点菜次数降序排序
            List<Map.Entry<Long, Integer>> sortedDishes = new ArrayList<>(dishCounts.entrySet());
            sortedDishes.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            List<Long> topDishIds = sortedDishes.stream().limit(3).map(Map.Entry::getKey).toList();
            for (Long id : topDishIds) {
                Dish d = dishService.getById(id);
                if (d != null) {
                    topDishNames.add(d.getName());
                }
            }

            // 根据采纳的菜品特征进行口味偏好打分
            for (Map.Entry<Long, Integer> entry : sortedDishes) {
                Dish d = dishService.getById(entry.getKey());
                if (d == null) continue;
                int count = entry.getValue();

                String name = d.getName() != null ? d.getName() : "";
                String taste = d.getTaste() != null ? d.getTaste() : "";
                String desc = d.getDescription() != null ? d.getDescription() : "";

                // 酸 score
                if (name.contains("番茄") || name.contains("酸菜") || name.contains("酸汤") || taste.contains("酸")) {
                    sourScore += count;
                }
                // 麻 score
                if (name.contains("花椒") || name.contains("麻辣") || taste.contains("麻")) {
                    numbScore += count;
                }
                // 甜 score
                if (name.contains("糖醋") || name.contains("蜜汁") || name.contains("蛋糕") || name.contains("双皮奶") || taste.contains("甜")) {
                    sweetScore += count;
                }
                // 嫩 score
                if (name.contains("滑鸡") || name.contains("鱼片") || name.contains("豆腐") || name.contains("蒸") || desc.contains("嫩")) {
                    tenderScore += count;
                }
            }
        }

        // 口味偏好汇总
        Set<String> historyTastes = new LinkedHashSet<>();
        // 1. 手动选过的偏好
        historyTastes.addAll(pastSelectedTastes);

        // 2. 根据点菜历史推导偏好
        if (sourScore >= 1) historyTastes.add("爱吃酸");
        if (numbScore >= 1) historyTastes.add("爱吃麻");
        if (tenderScore >= 1) historyTastes.add("喜欢嫩");
        if (sweetScore == 0 && visitsCount >= 2) {
            historyTastes.add("不爱甜");
        } else if (sweetScore >= 1) {
            historyTastes.remove("不爱甜");
        }

        // 如果历史选过甜，则不能有"不爱甜"
        if (pastSelectedTastes.contains("甜")) {
            historyTastes.remove("不爱甜");
        }

        List<String> historyTastesList = new ArrayList<>(historyTastes);
        profile.setHistoryTastes(historyTastesList);
        profile.setConsolidatedAvoids(new ArrayList<>(pastSelectedAvoids));
        profile.setConsolidatedAllergens(new ArrayList<>(pastSelectedAllergens));
        profile.setConsolidatedDiseases(new ArrayList<>(pastSelectedDiseases));
        profile.setConsolidatedDietLifestyles(new ArrayList<>(pastSelectedLifestyles));

        // 描述生成
        StringBuilder sb = new StringBuilder();
        sb.append("该顾客已到店消费 ").append(visitsCount).append(" 次。");
        if (!historyTastesList.isEmpty()) {
            sb.append("历史口味偏好：").append(String.join("、", historyTastesList)).append("；");
        }
        if (!topDishNames.isEmpty()) {
            sb.append("常点菜品：").append(String.join("、", topDishNames)).append("。");
        } else {
            sb.append("暂无常点菜品。");
        }

        List<String> historyRestrictions = new ArrayList<>();
        if (!pastSelectedAvoids.isEmpty()) historyRestrictions.add("忌口：" + String.join("、", pastSelectedAvoids));
        if (!pastSelectedAllergens.isEmpty()) historyRestrictions.add("过敏源：" + String.join("、", pastSelectedAllergens));
        if (!pastSelectedDiseases.isEmpty()) historyRestrictions.add("疾病禁忌：" + String.join("、", pastSelectedDiseases));
        if (!pastSelectedLifestyles.isEmpty()) historyRestrictions.add("饮食习惯：" + String.join("、", pastSelectedLifestyles));

        if (!historyRestrictions.isEmpty()) {
            sb.append("历史饮食忌口限制：").append(String.join(" | ", historyRestrictions)).append("。");
        }

        profile.setHistoryDescription(sb.toString());
        return profile;
    }
}
