package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.LoginDTO;
import com.example.foodrecommend.dto.LoginResultDTO;
import com.example.foodrecommend.entity.User;
import com.example.foodrecommend.mapper.UserMapper;
import com.example.foodrecommend.security.JwtTokenProvider;
import com.example.foodrecommend.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 微信API直连客户端（不走代理，否则IP白名单校验失败）
    private final OkHttpClient directClient = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Value("${wx.appid:}")
    private String wxAppId;
    @Value("${wx.secret:}")
    private String wxSecret;

    @Override
    public LoginResultDTO wxLogin(String code) {
        String openid;
        // 开发模式：未配置 secret 时使用 code 作为 mock openid
        if (wxSecret == null || wxSecret.isBlank() || wxSecret.startsWith("YOUR_")) {
            openid = "dev_" + code.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(code.length(), 20));
            log.info("开发模式微信登录, mock openid: {}", openid);
        } else {
            try {
                String url = "https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=" + wxAppId
                        + "&secret=" + wxSecret
                        + "&js_code=" + code
                        + "&grant_type=authorization_code";

                Request request = new Request.Builder().url(url).get().build();
                try (Response response = directClient.newCall(request).execute()) {
                    if (response.body() == null) {
                        throw new BusinessException("微信登录失败：无响应");
                    }
                    JsonNode json = objectMapper.readTree(response.body().string());
                    if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                        log.error("微信 jscode2session 失败: {}", json);
                        throw new BusinessException("微信登录失败: " + json.path("errmsg").asText());
                    }
                    openid = json.get("openid").asText();
                    log.info("微信 openid: {}", openid);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("调用微信登录接口异常", e);
                throw new BusinessException("微信登录服务不可用: " + e.getMessage());
            }
        }

        // 根据 openid 查找已有用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid)
        );

        if (user == null) {
            // 自动注册新服务员
            user = new User();
            user.setOpenid(openid);
            
            // 随机生成用户名，不包含 openid 信息，循环校验唯一性
            String randomUsername;
            while (true) {
                randomUsername = "wx_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10);
                Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, randomUsername));
                if (count == null || count == 0) {
                    break;
                }
            }
            user.setUsername(randomUsername);
            user.setRealName("微信服务员");
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRole("WAITER");
            user.setStatus(1);
            userMapper.insert(user);
            log.info("微信新用户自动注册: id={}, openid={}", user.getId(), openid);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginResultDTO result = new LoginResultDTO();
        result.setToken(token);
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        result.setRole(user.getRole());
        result.setPhone(user.getPhone());
        return result;
    }

    @Override
    public LoginResultDTO login(LoginDTO loginDTO) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, loginDTO.getUsername())
        );

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginResultDTO result = new LoginResultDTO();
        result.setToken(token);
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        result.setRole(user.getRole());
        result.setPhone(user.getPhone());
        return result;
    }

    @Override
    public User createWaiter(String username, String password, String realName, String phone) {
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
        if (password.length() > 64) {
            throw new BusinessException("密码长度不能超过64位");
        }
        // 检查用户名是否已存在
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(realName != null ? realName : username);
        user.setRole("WAITER");
        user.setPhone(phone);
        user.setStatus(1);

        userMapper.insert(user);
        log.info("创建员工账号成功: {}", username);
        return user;
    }

    @Override
    public List<User> listWaiters() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "WAITER")
                        .orderByDesc(User::getCreateTime)
        );
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("OWNER".equals(user.getRole())) {
            throw new BusinessException("不能修改管理员账号状态");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void deleteWaiter(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("OWNER".equals(user.getRole())) {
            throw new BusinessException("不能删除管理员账号");
        }
        userMapper.deleteById(id);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User updateProfile(Long id, String realName, String phone) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (realName != null) {
            user.setRealName(realName);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        userMapper.updateById(user);
        return user;
    }
}
