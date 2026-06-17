package com.example.foodrecommend.service;

import com.example.foodrecommend.dto.LoginDTO;
import com.example.foodrecommend.dto.LoginResultDTO;
import com.example.foodrecommend.entity.User;

import java.util.List;

public interface UserService {
    /** 登录：验证用户名密码，返回 JWT Token */
    LoginResultDTO login(LoginDTO loginDTO);

    /** 微信小程序登录：根据 wx code 自动注册/登录 */
    LoginResultDTO wxLogin(String code);

    /** 创建员工账号（仅老板可操作） */
    User createWaiter(String username, String password, String realName, String phone);

    /** 获取所有员工列表 */
    List<User> listWaiters();

    /** 禁用/启用员工账号 */
    void updateStatus(Long id, Integer status);

    /** 删除员工账号 */
    void deleteWaiter(Long id);

    /** 根据ID获取用户 */
    User getById(Long id);
}
