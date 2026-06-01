package com.example.foodrecommend.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendResultDTO {
    private Long recordId;
    private String imageUrl;
    private UserProfileDTO userProfile;
    private String summary;
    private List<RecommendDishDTO> recommendations;
}
