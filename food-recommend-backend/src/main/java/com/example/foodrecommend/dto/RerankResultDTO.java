package com.example.foodrecommend.dto;

import lombok.Data;

import java.util.List;

@Data
public class RerankResultDTO {
    private String summary;
    private List<RecommendDishDTO> recommendations;
}
