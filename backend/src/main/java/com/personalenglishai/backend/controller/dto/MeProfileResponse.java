package com.personalenglishai.backend.controller.dto;

/**
 * GET /api/users/me/profile 响应 data
 */
public class MeProfileResponse {
    private Long userId;
    private String email;
    private String nickname;

    public MeProfileResponse() {}

    public MeProfileResponse(Long userId, String email, String nickname) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
