package com.example.foodrecommend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackRequestDTO {

    @NotNull(message = "adopted 不能为空")
    private Boolean adopted;

    private Long adoptedDishId;

    @Min(value = 1, message = "quantity 至少为 1")
    @Max(value = 99, message = "quantity 不能超过 99")
    private Integer quantity;

    @Min(value = 1, message = "rating 范围 1-5")
    @Max(value = 5, message = "rating 范围 1-5")
    private Integer rating;

    @Size(max = 500, message = "note 不能超过 500 字")
    private String note;
}
