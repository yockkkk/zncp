package com.example.foodrecommend.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * JWT Token 中解析出的用户主体信息
 */
@Getter
@AllArgsConstructor
public class UserPrincipal {
    private Long userId;
    private String username;
    private String role;
}
