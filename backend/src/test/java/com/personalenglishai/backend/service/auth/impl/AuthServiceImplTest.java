package com.personalenglishai.backend.service.auth.impl;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.controller.auth.dto.LoginResponse;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.auth.LoginAttemptService;
import com.personalenglishai.backend.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Test
    @DisplayName("未验证邮箱密码正确时拒绝登录且不签发 token")
    void login_unverifiedEmailWithCorrectPassword_rejectedWithoutTokens() {
        User user = user(false);
        when(userMapper.findByEmail("u1@example.com")).thenReturn(user);
        when(passwordEncoder.matches("Abcd1234", "hash-1")).thenReturn(true);

        AuthServiceImpl service = service();

        assertThatThrownBy(() -> service.login(" U1@Example.com ", "Abcd1234"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);

        verify(jwtUtil, never()).generateAccessToken(1L, "Catalina", 0);
        verify(jwtUtil, never()).generateRefreshToken(1L, "Catalina", 0);
    }

    @Test
    @DisplayName("未验证邮箱密码错误时仍返回普通登录失败")
    void login_unverifiedEmailWithWrongPassword_returnsBadCredentials() {
        User user = user(false);
        when(userMapper.findByEmail("u1@example.com")).thenReturn(user);
        when(passwordEncoder.matches("wrongPass", "hash-1")).thenReturn(false);

        AuthServiceImpl service = service();

        assertThatThrownBy(() -> service.login("u1@example.com", "wrongPass"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);

        verify(loginAttemptService).recordFailure("u1@example.com");
        verify(jwtUtil, never()).generateAccessToken(1L, "Catalina", 0);
    }

    @Test
    @DisplayName("登录必须先校验密码再暴露未验证状态")
    void login_checksPasswordBeforeEmailVerification() {
        User user = user(false);
        when(userMapper.findByEmail("u1@example.com")).thenReturn(user);
        when(passwordEncoder.matches("Abcd1234", "hash-1")).thenReturn(true);

        AuthServiceImpl service = service();

        assertThatThrownBy(() -> service.login("u1@example.com", "Abcd1234"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);

        InOrder order = inOrder(passwordEncoder, jwtUtil);
        order.verify(passwordEncoder).matches("Abcd1234", "hash-1");
        verify(jwtUtil, never()).generateAccessToken(1L, "Catalina", 0);
    }

    @Test
    @DisplayName("已验证邮箱密码正确时正常签发 token")
    void login_verifiedEmailWithCorrectPassword_returnsTokens() {
        User user = user(true);
        when(userMapper.findByEmail("u1@example.com")).thenReturn(user);
        when(passwordEncoder.matches("Abcd1234", "hash-1")).thenReturn(true);
        when(jwtUtil.generateAccessToken(1L, "Catalina", 0)).thenReturn("access-1");
        when(jwtUtil.generateRefreshToken(1L, "Catalina", 0)).thenReturn("refresh-1");
        when(jwtUtil.getAccessTokenSeconds()).thenReturn(1800L);
        when(jwtUtil.getRefreshTokenSeconds()).thenReturn(259200L);

        AuthServiceImpl service = service();

        LoginResponse response = service.login("u1@example.com", "Abcd1234");

        assertThat(response.getToken()).isEqualTo("access-1");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-1");
        verify(loginAttemptService).clearAttempts("u1@example.com");
    }

    private AuthServiceImpl service() {
        return new AuthServiceImpl(userMapper, passwordEncoder, jwtUtil, loginAttemptService);
    }

    private User user(boolean emailVerified) {
        User user = new User();
        user.setId(1L);
        user.setEmail("u1@example.com");
        user.setNickname("Catalina");
        user.setPasswordHash("hash-1");
        user.setEmailVerified(emailVerified);
        user.setTokenVersion(0);
        return user;
    }
}
