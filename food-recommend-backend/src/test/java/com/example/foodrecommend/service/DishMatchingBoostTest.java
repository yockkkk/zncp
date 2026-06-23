package com.example.foodrecommend.service;

import com.example.foodrecommend.config.FeedbackBoostProperties;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.service.impl.DishMatchingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DishMatchingBoostTest {

    @Mock VectorSearchService vectorSearchService;
    @Mock DishMapper dishMapper;
    @Mock RecommendationHistoryService historyService;
    FeedbackBoostProperties props = new FeedbackBoostProperties();

    @InjectMocks DishMatchingServiceImpl service;

    private Dish dish(long id, String name, int sales) {
        Dish d = new Dish();
        d.setId(id); d.setName(name); d.setStatus(1); d.setVectorStatus(1);
        d.setStock(10); d.setSales(sales); d.setPrice(new BigDecimal("30"));
        d.setCategory("热菜"); d.setTags(""); d.setDescription(""); d.setTaste("");
        return d;
    }

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "props", props);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "historyService", historyService);
    }

    @Test
    void boost_pushes_historically_adopted_dish_to_top() {
        Dish a = dish(1, "宫保鸡丁", 50);  // sales 高
        Dish b = dish(2, "麻婆豆腐", 10);  // sales 低，但历史采纳多
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt())).thenReturn(List.of(1L, 2L));
        when(dishMapper.selectByIds(anyList())).thenReturn(List.of(a, b));
        when(historyService.lookupBoost(anyString())).thenReturn(Map.of(2L, 5));

        UserProfileDTO profile = new UserProfileDTO();
        List<Dish> result = service.matchDishes("q", profile, 10);
        assertThat(result.get(0).getId()).isEqualTo(2L);
    }

    @Test
    void cold_start_falls_back_to_sales_order() {
        Dish a = dish(1, "宫保鸡丁", 50);
        Dish b = dish(2, "麻婆豆腐", 10);
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt())).thenReturn(List.of(1L, 2L));
        when(dishMapper.selectByIds(anyList())).thenReturn(List.of(a, b));
        when(historyService.lookupBoost(anyString())).thenReturn(Map.of()); // 空

        UserProfileDTO profile = new UserProfileDTO();
        List<Dish> result = service.matchDishes("q", profile, 10);
        assertThat(result.get(0).getId()).isEqualTo(1L); // 销量优先
    }

    @Test
    void boost_does_not_bypass_safety_filter_halal() {
        Dish pork = dish(1, "回锅肉", 5);   // 含猪肉
        Dish veg  = dish(2, "麻婆豆腐", 1);
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt())).thenReturn(List.of(1L, 2L));
        when(dishMapper.selectByIds(anyList())).thenReturn(List.of(pork, veg));
        when(historyService.lookupBoost(anyString())).thenReturn(Map.of(1L, 10)); // 强 boost

        UserProfileDTO profile = new UserProfileDTO();
        com.example.foodrecommend.dto.GuestProfile g = new com.example.foodrecommend.dto.GuestProfile();
        g.setName("A"); g.setDietLifestyles(List.of("清真"));
        profile.setGuests(List.of(g));

        List<Dish> result = service.matchDishes("q", profile, 10);
        assertThat(result).extracting(Dish::getId).doesNotContain(1L);
    }

    @Test
    void disabled_flag_skips_boost_entirely() {
        props.setEnabled(false);
        Dish a = dish(1, "A", 50);
        Dish b = dish(2, "B", 10);
        when(vectorSearchService.searchSimilarDishes(anyString(), anyInt())).thenReturn(List.of(1L, 2L));
        when(dishMapper.selectByIds(anyList())).thenReturn(List.of(a, b));

        service.matchDishes("q", new UserProfileDTO(), 10);
        verify(historyService, never()).lookupBoost(anyString());
    }
}
