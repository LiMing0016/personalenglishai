package com.personalenglishai.backend.controller.dto;

/**
 * GET /api/users/me/profile 响应 data
 */
public class MeProfileResponse {
    private Long userId;
    private String email;
    private String nickname;
    private String studyStage;
    private Integer aiMode;
    private boolean emailVerified;
    private String phone;
    private boolean phoneVerified;
    private String avatarUrl;
    private String registerSource;
    private String createdAt;

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
    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }
    public Integer getAiMode() { return aiMode; }
    public void setAiMode(Integer aiMode) { this.aiMode = aiMode; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getRegisterSource() { return registerSource; }
    public void setRegisterSource(String registerSource) { this.registerSource = registerSource; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
