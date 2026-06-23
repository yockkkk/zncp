package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.FeedbackBoostProperties;
import com.example.foodrecommend.dto.GuestProfile;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.service.RecommendationHistoryService;
import com.example.foodrecommend.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DishMatchingServiceImpl.
 * Targets the rule-filter pipeline (halal/vegetarian/allergy/diabetes/multi-guest).
 *
 * Adaptations:
 * - Real impl uses DishMapper (not DishService) for selectByIds — mocked here.
 * - No EmbeddingService dependency in real impl; only VectorSearchService + DishMapper.
 * - Filtering is in-memory after vector search — we mock vectorSearchService to return IDs
 *   and dishMapper to return the candidate list.
 */
@ExtendWith(MockitoExtension.class)
class DishMatchingServiceImplTest {

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private DishMapper dishMapper;

    @Mock
    private RecommendationHistoryService historyService;

    @InjectMocks
    private DishMatchingServiceImpl service;

    @BeforeEach
    void setup() {
        FeedbackBoostProperties props = new FeedbackBoostProperties();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "props", props);
        // default boost map so existing tests aren't affected by boost logic
        lenient().when(historyService.lookupBoost(anyString())).thenReturn(Map.of());
    }

    /** Helper: build a dish that passes basic status/stock/vector checks */
    private Dish dish(Long id, String name, String tags, String description) {
        Dish d = new Dish();
        d.setId(id);
        d.setName(name);
        d.setTags(tags);
        d.setDescription(description);
        d.setStatus(1);
        d.setVectorStatus(1);
        d.setStock(10);
        d.setPrice(new BigDecimal("30"));
        d.setSales(5);
        return d;
    }

    private UserProfileDTO singleProfile() {
        return new UserProfileDTO();   // no guests → single mode
    }

    @Test
    void vectorSearchEmpty_throwsBusinessException() {
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt())).thenReturn(List.of());

        assertThatThrownBy(() -> service.matchDishes("query", singleProfile(), 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到相似菜品");
    }

    @Test
    void halalFilter_excludesPorkDish() {
        List<Dish> candidates = List.of(
                dish(1L, "宫保鸡丁", "辣,鸡", "鸡肉"),
                dish(2L, "回锅肉", "辣,猪", "五花肉,豆瓣酱"),
                dish(3L, "清蒸鲈鱼", "鲜", "鲈鱼")
        );
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(dishMapper.selectByIds(any())).thenReturn(candidates);

        UserProfileDTO profile = singleProfile();
        profile.setConsolidatedDietLifestyles(List.of("清真"));

        List<Dish> result = service.matchDishes("query", profile, 20);

        assertThat(result).extracting(Dish::getName)
                .doesNotContain("回锅肉")
                .contains("宫保鸡丁");
    }

    @Test
    void vegetarianFilter_excludesMeat() {
        List<Dish> candidates = List.of(
                dish(1L, "炒时蔬", "素食", "蔬菜"),
                dish(2L, "红烧鸡腿", "荤菜", "鸡腿"),
                dish(3L, "豆腐脑", "清淡", "豆腐")
        );
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(dishMapper.selectByIds(any())).thenReturn(candidates);

        UserProfileDTO profile = singleProfile();
        profile.setConsolidatedDietLifestyles(List.of("素食"));

        List<Dish> result = service.matchDishes("query", profile, 20);

        assertThat(result).extracting(Dish::getName)
                .doesNotContain("红烧鸡腿")
                .contains("炒时蔬");
    }

    @Test
    void seafoodAllergyFilter_excludesFishButKeepsYuxiangRousi() {
        // P3-20: 鱼香肉丝 name starts with "鱼香" → should NOT be filtered despite seafood allergy
        List<Dish> candidates = List.of(
                dish(1L, "鱼香肉丝", "川菜", "猪肉,木耳"),
                dish(2L, "清蒸鲈鱼", "鲜", "鲈鱼"),
                dish(3L, "番茄炒蛋", "家常", "鸡蛋,番茄")
        );
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(dishMapper.selectByIds(any())).thenReturn(candidates);

        UserProfileDTO profile = singleProfile();
        profile.setConsolidatedAllergens(List.of("海鲜"));

        List<Dish> result = service.matchDishes("query", profile, 20);

        assertThat(result).extracting(Dish::getName)
                .contains("鱼香肉丝")      // 鱼香 prefix → exempt
                .contains("番茄炒蛋")
                .doesNotContain("清蒸鲈鱼"); // contains 鱼 → filtered
    }

    @Test
    void diabetesFilter_excludesHighSugarDish() {
        List<Dish> candidates = List.of(
                dish(1L, "糖醋里脊", "糖醋", "猪肉"),
                dish(2L, "清炒时蔬", "素食", "蔬菜"),
                dish(3L, "蜜汁红薯", "甜品", "红薯")
        );
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(dishMapper.selectByIds(any())).thenReturn(candidates);

        UserProfileDTO profile = singleProfile();
        profile.setConsolidatedDiseases(List.of("糖尿病"));

        List<Dish> result = service.matchDishes("query", profile, 20);

        assertThat(result).extracting(Dish::getName)
                .doesNotContain("糖醋里脊", "蜜汁红薯")
                .contains("清炒时蔬");
    }

    @Test
    void multiGuest_halalAndVegetarianMerged_filtersCorrectly() {
        // Guest A: 清真; Guest B: 素食
        // 宫保鸡丁 (鸡) → OK for 清真, fails 素食 → at least 1 safe (Guest A) → include
        // 回锅肉 → contains 猪 → fails 清真 at table level → exclude
        // 炒时蔬 → safe for both → include
        List<Dish> candidates = List.of(
                dish(1L, "宫保鸡丁", "辣", "鸡肉,花生"),
                dish(2L, "回锅肉", "辣", "五花肉"),
                dish(3L, "炒时蔬", "素", "蔬菜")
        );
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        when(dishMapper.selectByIds(any())).thenReturn(candidates);

        GuestProfile guestA = new GuestProfile();
        guestA.setName("顾客A");
        guestA.setDietLifestyles(List.of("清真"));

        GuestProfile guestB = new GuestProfile();
        guestB.setName("顾客B");
        guestB.setDietLifestyles(List.of("素食"));

        UserProfileDTO profile = new UserProfileDTO();
        profile.setGuests(List.of(guestA, guestB));

        List<Dish> result = service.matchDishes("query", profile, 20);

        assertThat(result).extracting(Dish::getName)
                .doesNotContain("回锅肉");
        assertThat(result).extracting(Dish::getName)
                .contains("炒时蔬")
                .contains("宫保鸡丁");  // safe for at least one guest (Guest A: 清真)
    }
}
