package com.example.foodrecommend.dto;

import lombok.Data;

/**
 * 菜品推荐话术 DTO
 * Agent 5 (ScriptGenerationService) 的输出
 */
@Data
public class DishScriptDTO {
    private Long dishId;
    private String dishName;
    /** 针对这道菜的服务员推荐话术 */
    private String script;
}
