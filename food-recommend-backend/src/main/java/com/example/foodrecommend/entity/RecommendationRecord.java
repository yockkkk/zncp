package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("recommendation_record")
public class RecommendationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String phone;
    /** 发起推荐的服务员ID */
    private Long waiterId;
    /** 用户上传图片地址 */
    private String imageUrl;
    private String videoUrl;
    /** 场景图片URL（可选） */
    private String sceneImageUrl;
    /** 标签面板输入JSON */
    private String tagInputJson;
    /** AI分析出的用户画像JSON */
    private String userProfileJson;
    /** 生成的推荐查询文本 */
    private String queryText;
    /** 推荐菜品ID列表 */
    private String recommendedDishIds;
    /** 最终推荐结果JSON */
    private String resultJson;
    /** 话术生成结果JSON */
    private String scriptResultJson;
    /** 是否被采纳：1是，0否 */
    private Integer adopted;
    /** 被采纳的具体菜品ID（最近一次） */
    private Long adoptedDishId;
    /** 采纳数量（最近一次） */
    private Integer adoptedQuantity;
    private LocalDateTime createTime;

    /** 本条推荐的所有反馈记录（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private List<RecommendationFeedback> feedbacks;
}
