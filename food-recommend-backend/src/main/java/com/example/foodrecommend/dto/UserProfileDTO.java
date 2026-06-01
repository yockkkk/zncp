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
}
