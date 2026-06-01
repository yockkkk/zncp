package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dish")
public class Dish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer calories;
    private BigDecimal protein;
    private BigDecimal fat;
    private BigDecimal carbohydrate;
    private String taste;
    private String suitablePeople;
    private String scene;
    private String tags;
    private String imageUrl;
    private String description;
    private Integer sales;
    private Integer stock;
    private Integer status;
    private Integer vectorStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
