package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recommendation_record")
public class RecommendationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String imageUrl;
    private String videoUrl;
    private String userProfileJson;
    private String queryText;
    private String recommendedDishIds;
    private String resultJson;
    private LocalDateTime createTime;
}
