package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prompt_template")
public class PromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String content;
    private String type;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
