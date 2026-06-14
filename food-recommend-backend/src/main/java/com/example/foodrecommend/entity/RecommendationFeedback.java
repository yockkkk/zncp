package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推荐反馈实体
 */
@Data
@TableName("recommendation_feedback")
public class RecommendationFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long waiterId;
    private Long adoptedDishId;
    private Integer rating;
    private String note;
    private LocalDateTime createTime;
}
