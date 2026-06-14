package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.dto.LoginDTO;
import com.example.foodrecommend.dto.LoginResultDTO;
import com.example.foodrecommend.entity.User;
import com.example.foodrecommend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@RequestBody LoginDTO loginDTO) {
        LoginResultDTO result = userService.login(loginDTO);
        return Result.success("登录成功", result);
    }

    /**
     * 老板创建服务员账号
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody Map<String, String> body) {
        User user = userService.createWaiter(
                body.get("username"),
                body.get("password"),
                body.get("realName"),
                body.get("phone")
        );
        return Result.success("创建成功", user);
    }
}
