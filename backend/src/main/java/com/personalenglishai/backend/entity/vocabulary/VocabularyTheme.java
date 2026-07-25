package com.personalenglishai.backend.entity.vocabulary;

import java.time.LocalDateTime;

public class VocabularyTheme {
    private Long id;
    private String themeUid;
    private String ownerType;
    private Long userId;
    private String name;
    private String status;
    private Integer currentVersion;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThemeUid() { return themeUid; }
    public void setThemeUid(String themeUid) { this.themeUid = themeUid; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
