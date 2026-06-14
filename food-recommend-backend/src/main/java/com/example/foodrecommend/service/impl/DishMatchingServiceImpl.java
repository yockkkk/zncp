package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.service.DishMatchingService;
import com.example.foodrecommend.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 3 实现: 菜品匹配
 * 向量检索 + 规则过滤的组合管道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DishMatchingServiceImpl implements DishMatchingService {

    private final VectorSearchService vectorSearchService;
    private final DishMapper dishMapper;

    @Override
    public List<Dish> matchDishes(String queryText, UserProfileDTO profile, int topK) {
        // Step 1: 向量语义检索
        List<Long> dishIds = vectorSearchService.searchSimilarDishes(queryText, topK);

        if (dishIds.isEmpty()) {
            throw new BusinessException("未找到相似菜品，请确认菜品向量已生成");
        }

        log.info("Agent3-向量检索: 召回 {} 条候选菜品", dishIds.size());

        // Step 2: 从 MySQL 获取完整菜品数据
        List<Dish> candidateDishes = dishMapper.selectByIds(dishIds);

        // Step 3: 规则过滤
        List<Dish> filtered = ruleFilter(candidateDishes, profile);

        if (filtered.isEmpty()) {
            throw new BusinessException("过滤后无可推荐菜品");
        }

        log.info("Agent3-规则过滤: {} 条 → {} 条", candidateDishes.size(), filtered.size());
        return filtered;
    }

    /**
     * 规则过滤：上架状态 → 库存 → 价格匹配 → 销量排序 → Top 5
     */
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
}
