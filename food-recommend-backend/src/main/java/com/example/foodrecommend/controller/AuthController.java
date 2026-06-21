package com.example.foodrecommend.controller;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.common.Result;
import com.example.foodrecommend.dto.LoginDTO;
import com.example.foodrecommend.dto.LoginResultDTO;
import com.example.foodrecommend.dto.WxLoginDTO;
import com.example.foodrecommend.entity.User;
import com.example.foodrecommend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * 密码登录
     */
    @PostMapping("/login")
    public Result<LoginResultDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginResultDTO result = userService.login(loginDTO);
        return Result.success("登录成功", result);
    }

    /**
     * 微信小程序登录
     */
    @PostMapping("/wx-login")
    public Result<LoginResultDTO> wxLogin(@Valid @RequestBody WxLoginDTO wxLoginDTO) {
        if (wxLoginDTO.getCode() == null || wxLoginDTO.getCode().isEmpty()) {
            throw new BusinessException("code 不能为空");
        }
        LoginResultDTO result = userService.wxLogin(wxLoginDTO.getCode());
        return Result.success("登录成功", result);
    }

    /**
     * 老板创建服务员账号（仅OWNER可注册）
     */
    @PreAuthorize("hasRole('OWNER')")
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
