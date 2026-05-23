package com.personalenglishai.backend.entity.assistant;

import java.time.LocalDateTime;

public class AssistantConversationArchive {
    private Long id;
    private String archiveUid;
    private String conversationUid;
    private Long userId;
    private String title;
    private String summary;
    private Integer messageCount;
    private String archiveDir;
    private String markdownPath;
    private String jsonPath;
    private String metadataPath;
    private String checksum;
    private String status;
    private String errorMessage;
    private LocalDateTime archivedAt;
    private LocalDateTime restoredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getArchiveUid() { return archiveUid; }
    public void setArchiveUid(String archiveUid) { this.archiveUid = archiveUid; }
    public String getConversationUid() { return conversationUid; }
    public void setConversationUid(String conversationUid) { this.conversationUid = conversationUid; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }
    public String getArchiveDir() { return archiveDir; }
    public void setArchiveDir(String archiveDir) { this.archiveDir = archiveDir; }
    public String getMarkdownPath() { return markdownPath; }
    public void setMarkdownPath(String markdownPath) { this.markdownPath = markdownPath; }
    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }
    public String getMetadataPath() { return metadataPath; }
    public void setMetadataPath(String metadataPath) { this.metadataPath = metadataPath; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public LocalDateTime getRestoredAt() { return restoredAt; }
    public void setRestoredAt(LocalDateTime restoredAt) { this.restoredAt = restoredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
