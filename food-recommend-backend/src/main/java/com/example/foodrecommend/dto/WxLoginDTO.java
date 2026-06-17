package com.example.foodrecommend.dto;

import lombok.Data;

/**
 * 微信小程序登录请求
 */
@Data
public class WxLoginDTO {
    /** wx.login() 返回的临时 code */
    private String code;
}
