package com.personalenglishai.backend.entity.vocabulary;

import java.time.LocalDateTime;

public class VocabularyCard {
    private Long id;
    private String cardUid;
    private Long userId;
    private String language;
    private String originalTerm;
    private String normalizedTerm;
    private String displayTerm;
    private String templateKey;
    private Integer templateVersion;
    private String status;
    private String activeRevisionUid;
    private LocalDateTime lastCapturedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardUid() { return cardUid; }
    public void setCardUid(String cardUid) { this.cardUid = cardUid; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getOriginalTerm() { return originalTerm; }
    public void setOriginalTerm(String originalTerm) { this.originalTerm = originalTerm; }
    public String getNormalizedTerm() { return normalizedTerm; }
    public void setNormalizedTerm(String normalizedTerm) { this.normalizedTerm = normalizedTerm; }
    public String getDisplayTerm() { return displayTerm; }
    public void setDisplayTerm(String displayTerm) { this.displayTerm = displayTerm; }
    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }
    public Integer getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(Integer templateVersion) { this.templateVersion = templateVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActiveRevisionUid() { return activeRevisionUid; }
    public void setActiveRevisionUid(String activeRevisionUid) { this.activeRevisionUid = activeRevisionUid; }
    public LocalDateTime getLastCapturedAt() { return lastCapturedAt; }
    public void setLastCapturedAt(LocalDateTime lastCapturedAt) { this.lastCapturedAt = lastCapturedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
