package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.GuestProfile;
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
     * 规则过滤：上架状态 → 库存 → 价格匹配 → 安全特征检测 → 销量排序 → Top 20
     */
    private List<Dish> ruleFilter(List<Dish> dishes, UserProfileDTO profile) {
        return dishes.stream()
                .filter(dish -> dish.getStatus() != null && dish.getStatus() == 1)
                .filter(dish -> dish.getStock() != null && dish.getStock() > 0)
                .filter(dish -> priceMatch(dish, profile))
                .filter(dish -> isSafeForAtLeastOne(dish, profile))
                .sorted(Comparator.comparing(
                        d -> d.getSales() != null ? d.getSales() : 0,
                        Comparator.reverseOrder()))
                .limit(20)
                .collect(Collectors.toList());
    }

    private boolean isSafeForAtLeastOne(Dish dish, UserProfileDTO profile) {
        if (profile.getGuests() == null || profile.getGuests().isEmpty()) {
            // 单人模式，直接判断是否安全
            return isSafeForGuest(dish, "单人", profile.getConsolidatedAvoids(), profile.getConsolidatedAllergens(), profile.getConsolidatedDiseases(), profile.getConsolidatedDietLifestyles());
        }

        // 多人模式下，检查是否至少对一个顾客是安全的
        boolean hasHalal = false;
        for (GuestProfile guest : profile.getGuests()) {
            if (guest.getDietLifestyles() != null && guest.getDietLifestyles().contains("清真")) {
                hasHalal = true;
                break;
            }
        }

        // 如果整桌有人要求清真，则该菜品绝对不能含有猪肉/大肉成分
        if (hasHalal) {
            String dishName = dish.getName() != null ? dish.getName() : "";
            String dishTags = dish.getTags() != null ? dish.getTags() : "";
            String dishDesc = dish.getDescription() != null ? dish.getDescription() : "";
            String dishTaste = dish.getTaste() != null ? dish.getTaste() : "";
            String category = dish.getCategory() != null ? dish.getCategory() : "";
            String dishText = (dishName + " " + dishTags + " " + dishDesc + " " + dishTaste + " " + category).toLowerCase();
            if (dishText.contains("猪") || dishText.contains("大肠") || dishText.contains("肥肠") || dishText.contains("五花肉") || dishText.contains("排骨") || dishText.contains("培根") || dishText.contains("火腿") || dishText.contains("东坡肉") || dishText.contains("回锅肉") || dishText.contains("肉饼")) {
                log.info("安全拦截-整桌含有清真要求，过滤含猪肉菜品: dish={}", dish.getName());
                return false;
            }
        }

        int safeCount = 0;
        for (GuestProfile guest : profile.getGuests()) {
            if (isSafeForGuest(dish, guest.getName(), guest.getAvoidIngredients(), guest.getAllergens(), guest.getDiseases(), guest.getDietLifestyles())) {
                safeCount++;
            }
        }

        return safeCount > 0;
    }

    private boolean isSafeForGuest(Dish dish, String guestName, List<String> avoids, List<String> allergens, List<String> diseases, List<String> lifestyles) {
        String dishName = dish.getName() != null ? dish.getName() : "";
        String dishTags = dish.getTags() != null ? dish.getTags() : "";
        String dishDesc = dish.getDescription() != null ? dish.getDescription() : "";
        String dishTaste = dish.getTaste() != null ? dish.getTaste() : "";
        String category = dish.getCategory() != null ? dish.getCategory() : "";
        String dishText = (dishName + " " + dishTags + " " + dishDesc + " " + dishTaste + " " + category).toLowerCase();

        // 1. 忌口过滤
        if (avoids != null) {
            for (String avoid : avoids) {
                if (avoid == null || avoid.trim().isEmpty()) continue;
                String val = avoid.toLowerCase().trim();
                if (val.contains("辣")) {
                    if (dishText.contains("辣") || dishText.contains("剁椒") || dishText.contains("泡椒") || dishText.contains("川湘") || dishText.contains("麻辣") || dishText.contains("香辣") || dishText.contains("酸辣")) {
                        return false;
                    }
                } else if (dishText.contains(val)) {
                    return false;
                }
            }
        }

        // 2. 过敏源过滤
        if (allergens != null) {
            for (String allergen : allergens) {
                if (allergen == null || allergen.trim().isEmpty()) continue;
                String val = allergen.toLowerCase().trim();
                if (val.contains("海鲜")) {
                    if (dishText.contains("海鲜") || dishText.contains("鱼") || dishText.contains("虾") || dishText.contains("蟹") || dishText.contains("贝") || dishText.contains("蚝") || dishText.contains("鲍")) {
                        return false;
                    }
                } else if (val.contains("花生")) {
                    if (dishText.contains("花生") || dishText.contains("花生酱") || dishText.contains("坚果")) {
                        return false;
                    }
                } else if (dishText.contains(val)) {
                    return false;
                }
            }
        }

        // 3. 习惯/宗教过滤
        if (lifestyles != null) {
            for (String lifestyle : lifestyles) {
                if (lifestyle == null || lifestyle.trim().isEmpty()) continue;
                String val = lifestyle.trim();
                if ("清真".equals(val)) {
                    if (dishText.contains("猪") || dishText.contains("大肠") || dishText.contains("肥肠") || dishText.contains("五花肉") || dishText.contains("排骨") || dishText.contains("培根") || dishText.contains("火腿") || dishText.contains("东坡肉") || dishText.contains("回锅肉") || dishText.contains("肉饼")) {
                        return false;
                    }
                } else if ("素食".equals(val)) {
                    if (dishText.contains("肉") || dishText.contains("鸡") || dishText.contains("鸭") || dishText.contains("鱼") || dishText.contains("虾") || dishText.contains("蟹") || dishText.contains("牛") || dishText.contains("羊") || dishText.contains("猪") || dishText.contains("排骨") || dishText.contains("排") || dishText.contains("海鲜") || dishText.contains("肠") || dishText.contains("肝") || dishText.contains("肚")) {
                        return false;
                    }
                }
            }
        }

        // 4. 疾病过滤
        if (diseases != null) {
            for (String disease : diseases) {
                if (disease == null || disease.trim().isEmpty()) continue;
                String val = disease.trim();
                if (val.contains("糖尿病")) {
                    if (dishText.contains("甜") || dishText.contains("糖醋") || dishText.contains("蜜汁") || dishText.contains("拔丝") || dishText.contains("双皮奶") || dishText.contains("西米露") || dishText.contains("红豆")) {
                        return false;
                    }
                } else if (val.contains("痛风")) {
                    if (dishText.contains("海鲜") || dishText.contains("鱼") || dishText.contains("虾") || dishText.contains("蟹") || dishText.contains("贝") || dishText.contains("蚝") || dishText.contains("汤") || dishText.contains("大肠") || dishText.contains("肥肠") || dishText.contains("肚") || dishText.contains("肝")) {
                        return false;
                    }
                } else if (val.contains("高血压")) {
                    if (dishText.contains("腌") || dishText.contains("腊") || dishText.contains("炸") || dishText.contains("麻辣") || dishText.contains("重口味")) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean priceMatch(Dish dish, UserProfileDTO profile) {
        String level = profile.getEstimatedConsumptionLevel();
        BigDecimal price = dish.getPrice();
        if (price == null) return true;

        int peopleCount = (profile.getPeopleCount() != null && profile.getPeopleCount() > 0) ? profile.getPeopleCount() : 1;
        BigDecimal perPersonPrice = price;

        // 如果是多人套餐，按人均折算价格进行预算过滤
        if ("多人套餐".equals(dish.getCategory()) || 
            (dish.getName() != null && (dish.getName().contains("套餐") || dish.getName().contains("双人") || dish.getName().contains("人")))) {
            perPersonPrice = price.divide(BigDecimal.valueOf(peopleCount), 2, java.math.RoundingMode.HALF_UP);
        }

        if ("低".equals(level)) return perPersonPrice.compareTo(new BigDecimal("30")) <= 0;
        if ("中等".equals(level)) return perPersonPrice.compareTo(new BigDecimal("80")) <= 0;
        return true;
    }
}
