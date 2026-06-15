package com.example.foodrecommend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileDTO {
    private Integer peopleCount;
    private String ageRange;
    private String diningScene;
    private String clothingStyle;
    private String estimatedConsumptionLevel;
    private List<String> possiblePreferences;
    private String healthGoal;
    private List<String> recommendationKeywords;

    // 多人模式特有字段
    private List<GuestProfile> guests;
    private List<String> consolidatedAvoids;             // 合并后的忌口清单
    private List<String> consolidatedAllergens;          // 合并后的过敏源清单
    private List<String> consolidatedDiseases;           // 合并后的疾病清单
    private List<String> consolidatedDietLifestyles;     // 合并后的饮食习惯/宗教清单
}
