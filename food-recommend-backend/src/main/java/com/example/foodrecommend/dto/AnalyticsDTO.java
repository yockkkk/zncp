package com.example.foodrecommend.dto;

import lombok.Data;
import java.util.List;

/**
 * 数据分析 DTO — 老板看板数据
 */
@Data
public class AnalyticsDTO {

    /** 总推荐次数 */
    private Long totalRecommendations;

    /** 采纳率（百分比） */
    private Double adoptionRate;

    /** 菜品总数 */
    private Long totalDishes;

    /** 在岗服务员数 */
    private Long activeWaiters;

    /** 总销售额（流水） */
    private java.math.BigDecimal totalRevenue;

    /** 采纳率趋势 */
    private List<TrendPoint> adoptionTrend;

    /** 热门菜品 Top N */
    private List<DishStat> topDishes;

    /** 服务员表现 */
    private List<WaiterStat> waiterStats;

    @Data
    public static class TrendPoint {
        private String date;
        private Long total;
        private Long adopted;
    }

    @Data
    public static class DishStat {
        private Long dishId;
        private String dishName;
        private Long recommendCount;
        private Long adoptedCount;
    }

    @Data
    public static class WaiterStat {
        private Long waiterId;
        private String waiterName;
        private Long totalRecs;
        private Long adoptedCount;
        private Double adoptionRate;
        /** 营业额 */
        private java.math.BigDecimal revenue;
    }
}
