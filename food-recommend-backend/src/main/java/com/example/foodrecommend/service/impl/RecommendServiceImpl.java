package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.*;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final OssService ossService;
    private final VisionModelService visionModelService;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final AiRerankService aiRerankService;
    private final DishService dishService;
    private final DishMapper dishMapper;
    private final RecommendationRecordMapper recordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public RecommendResultDTO recommendByImage(MultipartFile file, Long userId) {
        String imageUrl = ossService.uploadFile(file);

        UserProfileDTO profile = visionModelService.analyzeImage(imageUrl);

        String queryText = buildRecommendQueryText(profile);

        List<Long> dishIds = vectorSearchService.searchSimilarDishes(queryText, 20);

        if (dishIds.isEmpty()) {
            throw new BusinessException("未找到相似菜品，请确认菜品向量已生成");
        }

        List<Dish> candidateDishes = dishMapper.selectByIds(dishIds);

        List<Dish> filteredDishes = ruleFilter(candidateDishes, profile);

        if (filteredDishes.isEmpty()) {
            throw new BusinessException("过滤后无可推荐菜品");
        }

        RerankResultDTO rerankResult = aiRerankService.rerank(profile, filteredDishes);

        Long recordId = saveRecommendationRecord(userId, imageUrl, profile, queryText,
                rerankResult.getRecommendations());

        RecommendResultDTO result = new RecommendResultDTO();
        result.setRecordId(recordId);
        result.setImageUrl(imageUrl);
        result.setUserProfile(profile);
        result.setSummary(rerankResult.getSummary());
        result.setRecommendations(rerankResult.getRecommendations());
        return result;
    }

    @Override
    public void batchRebuildVectors() {
        vectorSearchService.initCollection();

        List<Dish> dishes = dishService.listAll();
        int success = 0;
        int fail = 0;
        List<Dish> toUpdate = new ArrayList<>();

        for (Dish dish : dishes) {
            try {
                rebuildSingleDishVector(dish);
                dish.setVectorStatus(1);
                toUpdate.add(dish);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("菜品 {} 向量生成失败: {}", dish.getId(), e.getMessage());
            }
        }

        if (!toUpdate.isEmpty()) {
            dishService.updateBatchById(toUpdate);
        }

        log.info("批量向量生成完成: 成功={}, 失败={}", success, fail);
    }

    @Override
    public void rebuildDishVector(Long dishId) {
        Dish dish = dishService.getById(dishId);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        vectorSearchService.initCollection();
        rebuildSingleDishVector(dish);
        dish.setVectorStatus(1);
        dishService.updateById(dish);
    }

    private void rebuildSingleDishVector(Dish dish) {
        String embeddingText = dishService.buildDishEmbeddingText(dish);
        List<Float> vector = embeddingService.getEmbedding(embeddingText);

        DishVectorDTO payload = new DishVectorDTO();
        payload.setDishId(dish.getId());
        payload.setName(dish.getName());
        payload.setCategory(dish.getCategory());
        double price = dish.getPrice() != null ? dish.getPrice().doubleValue() : 0;
        payload.setPrice(price);
        payload.setCalories(dish.getCalories() != null ? dish.getCalories() : 0);
        payload.setProtein(dish.getProtein() != null ? dish.getProtein().doubleValue() : 0);
        payload.setTaste(dish.getTaste());
        payload.setTags(splitToList(dish.getTags()));
        payload.setSuitablePeople(splitToList(dish.getSuitablePeople()));
        payload.setScene(splitToList(dish.getScene()));

        vectorSearchService.upsertDishVector(dish.getId(), vector, payload);
    }

    private String buildRecommendQueryText(UserProfileDTO profile) {
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

    private List<Dish> ruleFilter(List<Dish> dishes, UserProfileDTO profile) {
        return dishes.stream()
                .filter(dish -> dish.getStatus() != null && dish.getStatus() == 1)
                .filter(dish -> dish.getStock() != null && dish.getStock() > 0)
                .filter(dish -> priceMatch(dish, profile))
                .sorted(Comparator.comparing(
                        d -> d.getSales() != null ? d.getSales() : 0,
                        Comparator.reverseOrder()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private boolean priceMatch(Dish dish, UserProfileDTO profile) {
        String level = profile.getEstimatedConsumptionLevel();
        BigDecimal price = dish.getPrice();
        if (price == null) return true;

        if ("低".equals(level)) return price.compareTo(new BigDecimal("30")) <= 0;
        if ("中等".equals(level)) return price.compareTo(new BigDecimal("80")) <= 0;
        return true;
    }

    private Long saveRecommendationRecord(Long userId, String imageUrl, UserProfileDTO profile,
                                           String queryText, List<RecommendDishDTO> results) {
        try {
            RecommendationRecord record = new RecommendationRecord();
            record.setUserId(userId);
            record.setImageUrl(imageUrl);
            record.setUserProfileJson(objectMapper.writeValueAsString(profile));
            record.setQueryText(queryText);

            String dishIds = results.stream()
                    .filter(r -> r.getDishId() != null)
                    .map(r -> String.valueOf(r.getDishId()))
                    .collect(Collectors.joining(","));
            record.setRecommendedDishIds(dishIds);
            record.setResultJson(objectMapper.writeValueAsString(results));

            recordMapper.insert(record);
            return record.getId();
        } catch (Exception e) {
            log.error("保存推荐记录失败", e);
            return null;
        }
    }

    private List<String> splitToList(String str) {
        if (str == null || str.isEmpty()) return List.of();
        return Arrays.asList(str.split("[,，]"));
    }
}
