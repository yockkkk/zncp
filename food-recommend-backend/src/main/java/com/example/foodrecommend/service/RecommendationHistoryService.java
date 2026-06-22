package com.example.foodrecommend.service;

import java.util.List;
import java.util.Map;

public interface RecommendationHistoryService {
    /** 异步：把一条已采纳 record 写入历史向量库 */
    void indexAdoption(Long recordId, String queryText, List<Long> adoptedDishIds, Long waiterId);

    /** 查相似历史采纳。返回 {dishId -> 累计采纳次数}；冷启动/失败/disabled 时返回空 map。 */
    Map<Long, Integer> lookupBoost(String queryText);
}
