package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.UserProfileDTO;

public interface VisionModelService {
    UserProfileDTO analyzeImage(String imageUrl);
}
