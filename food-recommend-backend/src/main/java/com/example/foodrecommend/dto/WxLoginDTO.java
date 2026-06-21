package com.example.foodrecommend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信小程序登录请求
 */
@Data
public class WxLoginDTO {
    /** wx.login() 返回的临时 code */
    @NotBlank(message = "code 不能为空")
    private String code;
}
