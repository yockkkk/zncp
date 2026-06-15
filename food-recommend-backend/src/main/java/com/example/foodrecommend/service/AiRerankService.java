package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.RerankResultDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;

import java.util.List;

public interface AiRerankService {
    RerankResultDTO rerank(UserProfileDTO profile, List<Dish> candidateDishes, String remark);

    default RerankResultDTO rerank(UserProfileDTO profile, List<Dish> candidateDishes) {
        return rerank(profile, candidateDishes, null);
    }
}
