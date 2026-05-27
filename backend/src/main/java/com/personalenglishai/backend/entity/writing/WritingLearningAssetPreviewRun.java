package com.personalenglishai.backend.entity.writing;

import java.time.LocalDateTime;

public class WritingLearningAssetPreviewRun {
    private Long id;
    private String runUid;
    private Long documentId;
    private Long userId;
    private String status;
    private String model;
    private String summary;
    private String resultJson;
    private String errorMessage;
    private Long inputTokenCount;
    private Long outputTokenCount;
    private Integer itemCount;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunUid() { return runUid; }
    public void setRunUid(String runUid) { this.runUid = runUid; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getInputTokenCount() { return inputTokenCount; }
    public void setInputTokenCount(Long inputTokenCount) { this.inputTokenCount = inputTokenCount; }
    public Long getOutputTokenCount() { return outputTokenCount; }
    public void setOutputTokenCount(Long outputTokenCount) { this.outputTokenCount = outputTokenCount; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
