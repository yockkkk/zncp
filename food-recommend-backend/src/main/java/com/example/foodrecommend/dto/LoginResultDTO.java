package com.example.foodrecommend.dto;

import lombok.Data;

/**
 * 登录结果 DTO
 */
@Data
public class LoginResultDTO {
    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String role;
    private String phone;
}
