package com.example.foodrecommend.dto;

import lombok.Data;
import java.util.List;

/**
 * 标签面板输入 DTO
 * 服务员通过标签面板快速勾选的顾客画像信息
 */
@Data
public class TagInputDTO {
    /** 用餐人数：1, 2, 3-4, 5+ */
    private String peopleCount;

    /** 用餐场景：便餐, 约会, 商务, 家庭, 朋友聚餐 */
    private String diningScene;

    /** 口味偏好（多选）：辣, 清淡, 甜, 咸, 无偏好 */
    private List<String> tastePreferences;

    /** 预算等级：实惠, 中等, 高端, 不限 */
    private String budgetLevel;

    /** 饮食限制：无, 素食, 低脂, 高蛋白 */
    private String dietaryRestriction;

    /** 用餐时段：早餐, 午餐, 晚餐, 夜宵 */
    private String mealTime;

    /** 多人桌详细顾客画像列表 */
    private List<GuestProfile> guests;
}
