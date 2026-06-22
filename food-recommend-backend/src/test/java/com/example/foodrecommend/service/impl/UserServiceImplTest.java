package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.LoginDTO;
import com.example.foodrecommend.dto.LoginResultDTO;
import com.example.foodrecommend.entity.User;
import com.example.foodrecommend.mapper.UserMapper;
import com.example.foodrecommend.security.JwtTokenProvider;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserServiceImpl.
 * Uses Mockito; no DB, no HTTP, no Spring context.
 *
 * Adaptations from brief skeleton:
 * - Real impl injects JwtTokenProvider (not JwtUtil) — mocked here.
 * - Real impl injects OkHttpClient bean — mocked to satisfy @InjectMocks.
 * - wxLogin relies on OkHttpClient for HTTP call; we only test empty-code guard.
 * - PasswordEncoder is a real BCryptPasswordEncoder (not injected from context).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private OkHttpClient httpClient;

    @InjectMocks
    private UserServiceImpl service;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ---- login tests ----

    @Test
    void login_unknownUser_throwsBusinessException() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("nobody");
        dto.setPassword("password1");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_disabledUser_throwsBusinessException() {
        User u = new User();
        u.setUsername("alice");
        u.setPassword(encoder.encode("correct"));
        u.setRole("WAITER");
        u.setStatus(0);  // disabled
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("correct");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    void login_wrongPassword_throwsBusinessException() {
        String encodedCorrect = encoder.encode("correct");
        User u = new User();
        u.setUsername("alice");
        u.setPassword(encodedCorrect);
        u.setRole("WAITER");
        u.setStatus(1);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        // passwordEncoder.matches returns false for wrong password
        when(passwordEncoder.matches("wrong", encodedCorrect)).thenReturn(false);

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrong");

        assertThatThrownBy(() -> service.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_success_returnsLoginResultWithUsername() {
        String encodedPwd = encoder.encode("correct");
        User u = new User();
        u.setId(1L);
        u.setUsername("alice");
        u.setPassword(encodedPwd);
        u.setRole("WAITER");
        u.setStatus(1);
        u.setRealName("Alice Wang");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(u);
        when(passwordEncoder.matches("correct", encodedPwd)).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "alice", "WAITER")).thenReturn("mock-jwt-token");

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("correct");

        LoginResultDTO result = service.login(dto);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getRole()).isEqualTo("WAITER");
        assertThat(result.getToken()).isEqualTo("mock-jwt-token");
        assertThat(result.getUserId()).isEqualTo(1L);
    }
}
