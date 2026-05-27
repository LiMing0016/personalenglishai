package com.personalenglishai.backend.entity.writing;

import java.time.LocalDateTime;

public class WritingDocumentAssetSnapshot {
    private Long id;
    private Long documentId;
    private Long userId;
    private String snapshotUid;
    private String markdownContent;
    private String snapshotJson;
    private Integer latestRevision;
    private Integer evaluationCount;
    private Integer coachMessageCount;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSnapshotUid() { return snapshotUid; }
    public void setSnapshotUid(String snapshotUid) { this.snapshotUid = snapshotUid; }
    public String getMarkdownContent() { return markdownContent; }
    public void setMarkdownContent(String markdownContent) { this.markdownContent = markdownContent; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public Integer getLatestRevision() { return latestRevision; }
    public void setLatestRevision(Integer latestRevision) { this.latestRevision = latestRevision; }
    public Integer getEvaluationCount() { return evaluationCount; }
    public void setEvaluationCount(Integer evaluationCount) { this.evaluationCount = evaluationCount; }
    public Integer getCoachMessageCount() { return coachMessageCount; }
    public void setCoachMessageCount(Integer coachMessageCount) { this.coachMessageCount = coachMessageCount; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
