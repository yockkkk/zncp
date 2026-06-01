package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.DishVectorDTO;

import java.util.List;

public interface VectorSearchService {
    void initCollection();

    void upsertDishVector(Long dishId, List<Float> vector, DishVectorDTO payload);

    void deleteDishVector(Long dishId);

    List<Long> searchSimilarDishes(String queryText, int topK);
}
