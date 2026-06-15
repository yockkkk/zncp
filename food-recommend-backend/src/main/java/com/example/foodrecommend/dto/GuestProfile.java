package com.example.foodrecommend.dto;

import lombok.Data;
import java.util.List;

/**
 * 顾客画像特征 DTO
 */
@Data
public class GuestProfile {
    private String name;                       // 顾客名称（如：顾客A）
    private List<String> avoidIngredients;    // 忌口/不吃
    private List<String> allergens;           // 过敏源
    private List<String> diseases;            // 疾病/慢病禁忌
    private List<String> dietLifestyles;      // 宗教/习惯
    private List<String> tastes;              // 口味偏好/特殊要求
}
