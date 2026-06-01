package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.RecommendResultDTO;
import org.springframework.web.multipart.MultipartFile;

public interface RecommendService {
    RecommendResultDTO recommendByImage(MultipartFile file, Long userId);

    void batchRebuildVectors();

    void rebuildDishVector(Long dishId);
}
