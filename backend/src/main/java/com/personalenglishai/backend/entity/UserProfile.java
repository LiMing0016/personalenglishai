package com.personalenglishai.backend.entity;

/**
 * 用户档案实体
 * 注意：user_id 作为主键，不使用独立的 id 字段
 */
public class UserProfile {
    private Long userId; // 主键，对应 users.id
    private String studyStage; // 学段：如 "四级"、"六级"、"考研" 等
    private Integer aiMode; // AI模式：0=普通模式，1=学段模式

    public UserProfile() {
    }

    public UserProfile(Long userId, String studyStage, Integer aiMode) {
        this.userId = userId;
        this.studyStage = studyStage;
        this.aiMode = aiMode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public Integer getAiMode() {
        return aiMode;
    }

    public void setAiMode(Integer aiMode) {
        this.aiMode = aiMode;
    }
}

