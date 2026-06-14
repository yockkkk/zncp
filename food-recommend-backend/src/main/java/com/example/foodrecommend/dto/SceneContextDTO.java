package com.example.foodrecommend.dto;

import lombok.Data;
import java.util.List;

/**
 * 场景感知结果 DTO
 * Agent 1 (ScenePerceptionService) 的输出，分析用餐环境照片
 */
@Data
public class SceneContextDTO {
    /** 桌型：小桌, 大桌, 圆桌, 包间 */
    private String tableType;

    /** 推断的用餐时段 */
    private String mealTime;

    /** 环境氛围：休闲, 正式, 商务, 家庭 */
    private String atmosphere;

    /** 估计用餐人数 */
    private Integer estimatedPeopleCount;

    /** 环境提示信息 */
    private List<String> hints;
}
