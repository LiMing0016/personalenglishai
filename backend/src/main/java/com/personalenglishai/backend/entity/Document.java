package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

/**
 * 文档主表实体
 */
public class Document {

    private Long id;
    private String publicId;
    private String tenantId;
    private String workspaceId;
    private Long ownerUserId;
    private String title;
    private String taskPrompt;
    private String taskPromptHash;
    private Integer initialScore;
    private Integer latestScore;
    private Integer submitCount;
    private Integer status;
    private Integer latestRevision;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTaskPrompt() { return taskPrompt; }
    public void setTaskPrompt(String taskPrompt) { this.taskPrompt = taskPrompt; }
    public String getTaskPromptHash() { return taskPromptHash; }
    public void setTaskPromptHash(String taskPromptHash) { this.taskPromptHash = taskPromptHash; }
    public Integer getInitialScore() { return initialScore; }
    public void setInitialScore(Integer initialScore) { this.initialScore = initialScore; }
    public Integer getLatestScore() { return latestScore; }
    public void setLatestScore(Integer latestScore) { this.latestScore = latestScore; }
    public Integer getSubmitCount() { return submitCount; }
    public void setSubmitCount(Integer submitCount) { this.submitCount = submitCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getLatestRevision() { return latestRevision; }
    public void setLatestRevision(Integer latestRevision) { this.latestRevision = latestRevision; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
