package com.example.foodrecommend.dto;

import lombok.Data;
import java.util.List;

/**
 * 话术生成完整结果 DTO
 * Agent 5 返回的完整话术结构
 */
@Data
public class ScriptResultDTO {
    /** 服务员对顾客说的开场话术 */
    private String openingScript;
    /** 每道菜的具体推荐话术 */
    private List<DishScriptDTO> dishScripts;
}
