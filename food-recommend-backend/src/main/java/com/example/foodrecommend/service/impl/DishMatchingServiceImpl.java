package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.FeedbackBoostProperties;
import com.example.foodrecommend.dto.GuestProfile;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.service.DishMatchingService;
import com.example.foodrecommend.service.RecommendationHistoryService;
import com.example.foodrecommend.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final RecommendationHistoryService historyService;
    private final FeedbackBoostProperties props;

    @Override
    public List<Dish> matchDishes(String queryText, UserProfileDTO profile, int topK) {
        List<Long> dishIds = vectorSearchService.searchSimilarDishes(queryText, topK);
        if (dishIds.isEmpty()) throw new BusinessException("未找到相似菜品，请确认菜品向量已生成");
        log.info("Agent3-向量检索: 召回 {} 条候选菜品", dishIds.size());

        List<Dish> candidateDishes = dishMapper.selectByIds(dishIds);

        // 安全/价格/库存过滤先做（不动）
        List<Dish> safe = candidateDishes.stream()
                .filter(d -> d.getStatus() != null && d.getStatus() == 1)
                .filter(d -> d.getVectorStatus() != null && d.getVectorStatus() == 1)
                .filter(d -> d.getStock() != null && d.getStock() > 0)
                .filter(d -> priceMatch(d, profile))
                .filter(d -> isSafeForAtLeastOne(d, profile))
                .collect(Collectors.toList());

        if (safe.isEmpty()) throw new BusinessException("过滤后无可推荐菜品");

        // 应用 boost
        Map<Long, Integer> boost = props.isEnabled()
                ? historyService.lookupBoost(queryText)
                : Collections.emptyMap();

        int maxSales = safe.stream().mapToInt(d -> d.getSales() != null ? d.getSales() : 0).max().orElse(0);
        final int saleNorm = maxSales + 1;

        List<Dish> sorted = safe.stream()
                .sorted((x, y) -> Double.compare(score(y, boost, saleNorm), score(x, boost, saleNorm)))
                .limit(20)
                .collect(Collectors.toList());

        log.info("Agent3-规则过滤: {} 条 → {} 条 boost={}", candidateDishes.size(), sorted.size(), boost.size());
        if (!boost.isEmpty()) {
            log.info("boost.applied top3={}", sorted.stream().limit(3).map(Dish::getId).toList());
        }
        return sorted;
    }

    private double score(Dish d, Map<Long, Integer> boost, int saleNorm) {
        double base = (d.getSales() != null ? d.getSales() : 0) / (double) saleNorm;
        int count = boost.getOrDefault(d.getId(), 0);
        double bst = Math.min(count, props.getBoostCap()) / (double) props.getBoostCap();
        return base + props.getWeight() * bst;
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
                    // P3-20: 排除名称含"鱼"但实际不含海鲜的菜品（如鱼香茄子、鱼香肉丝）
                    if (!dishName.contains("鱼香")) {
                        if (dishText.contains("海鲜") || dishText.contains("鱼") || dishText.contains("虾") || dishText.contains("蟹") || dishText.contains("贝") || dishText.contains("蚝") || dishText.contains("鲍")) {
                            return false;
                        }
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

        // 套餐按实际所需份数折算人均价格：套餐容量 / 人数 → 所需份数，再计算人均
        int servingSize = guessServingSize(dish);
        if (servingSize > 1) {
            int packagesNeeded = (int) Math.ceil((double) peopleCount / servingSize);
            BigDecimal totalCost = price.multiply(BigDecimal.valueOf(packagesNeeded));
            perPersonPrice = totalCost.divide(BigDecimal.valueOf(peopleCount), 2, java.math.RoundingMode.HALF_UP);
        }

        if ("低".equals(level)) return perPersonPrice.compareTo(new BigDecimal("30")) <= 0;
        if ("中等".equals(level)) return perPersonPrice.compareTo(new BigDecimal("80")) <= 0;
        return true;
    }

    /** 猜测套餐适合人数 */
    private int guessServingSize(Dish dish) {
        String name = dish.getName() != null ? dish.getName() : "";
        if (name.contains("单人") || name.contains("一人")) return 1;
        if (name.contains("双人") || name.contains("情侣") || name.contains("二人") || name.contains("两人")) return 2;
        if (name.contains("三人") || name.contains("三口")) return 3;
        if (name.contains("四人") || name.contains("四人")) return 4;
        if (name.contains("六人")) return 6;
        if (name.contains("八人")) return 8;
        if ("多人套餐".equals(dish.getCategory())) return 2; // 默认双人起
        if (name.contains("套餐")) return 2;
        return 1;
    }
}
