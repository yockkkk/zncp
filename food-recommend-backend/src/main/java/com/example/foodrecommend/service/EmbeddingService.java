package com.example.foodrecommend.service;

import java.util.List;

public interface EmbeddingService {
    List<Float> getEmbedding(String text);
}
