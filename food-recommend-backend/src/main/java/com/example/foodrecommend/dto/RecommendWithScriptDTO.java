package com.example.foodrecommend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * 带话术的推荐结果 DTO
 * 扩展 RecommendResultDTO，增加话术字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RecommendWithScriptDTO extends RecommendResultDTO {
    /** 场景分析结果 */
    private SceneContextDTO sceneContext;
    /** 开场话术 */
    private String openingScript;
    /** 每道菜的具体推荐话术 */
    private List<DishScriptDTO> dishScripts;
}
