package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class WritingTaskMetadata {
    private Long id;
    private Long documentId;
    private String documentPublicId;
    private Long userId;
    private String studyStage;
    private String assistantMode;
    private String promptText;
    private String taskType;
    private String centralTask;
    private String mustAnswerPointsJson;
    private String writingFocusJson;
    private String riskPointsJson;
    private String recommendedStructureJson;
    private String rubricFocusJson;
    private String metadataVersion;
    private String rubricSource;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocumentPublicId() { return documentPublicId; }
    public void setDocumentPublicId(String documentPublicId) { this.documentPublicId = documentPublicId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }
    public String getAssistantMode() { return assistantMode; }
    public void setAssistantMode(String assistantMode) { this.assistantMode = assistantMode; }
    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getCentralTask() { return centralTask; }
    public void setCentralTask(String centralTask) { this.centralTask = centralTask; }
    public String getMustAnswerPointsJson() { return mustAnswerPointsJson; }
    public void setMustAnswerPointsJson(String mustAnswerPointsJson) { this.mustAnswerPointsJson = mustAnswerPointsJson; }
    public String getWritingFocusJson() { return writingFocusJson; }
    public void setWritingFocusJson(String writingFocusJson) { this.writingFocusJson = writingFocusJson; }
    public String getRiskPointsJson() { return riskPointsJson; }
    public void setRiskPointsJson(String riskPointsJson) { this.riskPointsJson = riskPointsJson; }
    public String getRecommendedStructureJson() { return recommendedStructureJson; }
    public void setRecommendedStructureJson(String recommendedStructureJson) { this.recommendedStructureJson = recommendedStructureJson; }
    public String getRubricFocusJson() { return rubricFocusJson; }
    public void setRubricFocusJson(String rubricFocusJson) { this.rubricFocusJson = rubricFocusJson; }
    public String getMetadataVersion() { return metadataVersion; }
    public void setMetadataVersion(String metadataVersion) { this.metadataVersion = metadataVersion; }
    public String getRubricSource() { return rubricSource; }
    public void setRubricSource(String rubricSource) { this.rubricSource = rubricSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
