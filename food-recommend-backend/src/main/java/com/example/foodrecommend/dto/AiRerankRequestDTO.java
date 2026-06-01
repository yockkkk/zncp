package com.example.foodrecommend.dto;

import com.example.foodrecommend.entity.Dish;
import lombok.Data;

import java.util.List;

@Data
public class AiRerankRequestDTO {
    private UserProfileDTO userProfile;
    private List<Dish> candidateDishes;
}
