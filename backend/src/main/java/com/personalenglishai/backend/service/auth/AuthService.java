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
     * 登录：email 唯一，BCrypt 校验，返回 access + refresh token
     */
    LoginResponse login(String email, String password);

    /**
     * 刷新：用有效的 refresh token 换取新的 access token
     */
    LoginResponse refresh(String refreshToken);

    /**
     * 手机验证码登录：验证码已在 controller 层验证通过
     */
    LoginResponse loginByPhone(String phone);

    /**
     * 手机密码登录
     */
    LoginResponse loginByPhonePassword(String phone, String password);

    /**
     * 手机注册（免密）：手机号 + 昵称，验证码已在 controller 层验证通过。
     * 返回 LoginResponse 以支持注册后自动登录。
     */
    LoginResponse registerByPhone(String phone, String nickname);

    /**
     * 已登录用户修改密码：验证当前密码，更新为新密码。
     * 更新后 token_version 递增，旧 token 失效。
     */
    void changePassword(Long userId, String currentPassword, String newPassword);
}
