package com.example.foodrecommend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecommendDishDTO {
    private Long dishId;
    private String name;
    private BigDecimal price;
    private Integer calories;
    private BigDecimal protein;
    private Integer rank;
    private Integer score;
    private String reason;
    private String nutritionComment;
    private String costPerformanceComment;
    private String imageUrl;
    private java.util.List<String> suitableFor;
}
