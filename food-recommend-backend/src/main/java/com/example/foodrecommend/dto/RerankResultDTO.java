package com.example.foodrecommend.dto;

import lombok.Data;

import java.util.List;

@Data
public class RerankResultDTO {
    private String summary;
    private List<RecommendDishDTO> recommendations;
    /** 合并 Agent5 输出：开场话术，存在则可跳过 Agent5 单独调用 */
    private String openingScript;
    /** 合并 Agent5 输出：每道菜的推荐话术 */
    private List<DishScriptDTO> dishScripts;
}
