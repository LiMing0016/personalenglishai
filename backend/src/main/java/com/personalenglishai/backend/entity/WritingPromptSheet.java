package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class WritingPromptSheet {

    private Long id;
    private String paper;
    private Long documentId;
    private String studyStage;
    private String sourceType;
    private String taskType;
    private String promptType;
    private String topicTitle;
    private String directions;
    private String promptText;
    private String requirementsText;
    private String genre;
    private Integer wordCountMin;
    private Integer wordCountMax;
    private String attachmentType;
    private String attachmentPayloadJson;
    private String structuredPayloadJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPaper() { return paper; }
    public void setPaper(String paper) { this.paper = paper; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getPromptType() { return promptType; }
    public void setPromptType(String promptType) { this.promptType = promptType; }
    public String getTopicTitle() { return topicTitle; }
    public void setTopicTitle(String topicTitle) { this.topicTitle = topicTitle; }
    public String getDirections() { return directions; }
    public void setDirections(String directions) { this.directions = directions; }
    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }
    public String getRequirementsText() { return requirementsText; }
    public void setRequirementsText(String requirementsText) { this.requirementsText = requirementsText; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public Integer getWordCountMin() { return wordCountMin; }
    public void setWordCountMin(Integer wordCountMin) { this.wordCountMin = wordCountMin; }
    public Integer getWordCountMax() { return wordCountMax; }
    public void setWordCountMax(Integer wordCountMax) { this.wordCountMax = wordCountMax; }
    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
    public String getAttachmentPayloadJson() { return attachmentPayloadJson; }
    public void setAttachmentPayloadJson(String attachmentPayloadJson) { this.attachmentPayloadJson = attachmentPayloadJson; }
    public String getStructuredPayloadJson() { return structuredPayloadJson; }
    public void setStructuredPayloadJson(String structuredPayloadJson) { this.structuredPayloadJson = structuredPayloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
