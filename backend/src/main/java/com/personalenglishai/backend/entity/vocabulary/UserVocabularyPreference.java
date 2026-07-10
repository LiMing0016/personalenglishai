package com.personalenglishai.backend.entity.vocabulary;

import java.time.LocalDateTime;

public class UserVocabularyPreference {
    private Long id;
    private Long userId;
    private String defaultTemplateKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDefaultTemplateKey() { return defaultTemplateKey; }
    public void setDefaultTemplateKey(String defaultTemplateKey) { this.defaultTemplateKey = defaultTemplateKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
