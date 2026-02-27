package com.personalenglishai.backend.controller.auth.dto;

/**
 * 注册响应 DTO（仅返回 userId，不含 password/password_hash）
 */
public class RegisterResponse {
    private Long userId;

    public RegisterResponse() {}

    public RegisterResponse(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
