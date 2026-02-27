package com.personalenglishai.backend.service.auth;

import com.personalenglishai.backend.controller.auth.dto.LoginResponse;

/**
 * 认证服务
 */
public interface AuthService {
    /**
     * 注册：邮箱规范化、唯一校验、BCrypt 存储，返回 userId
     */
    Long register(String email, String password, String nickname);

    /**
     * 登录：email 唯一，BCrypt 校验，返回 JWT
     */
    LoginResponse login(String email, String password);
}
