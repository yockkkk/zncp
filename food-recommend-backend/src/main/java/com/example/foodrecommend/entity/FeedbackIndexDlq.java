package com.example.foodrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback_index_dlq")
public class FeedbackIndexDlq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private String error;
    private Integer retryCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
