package com.personalenglishai.backend.dto.writing;

import java.time.LocalDateTime;

/**
 * 写作会话元数据查询返回值：共享上下文 + 考试子表信息。
 */
public class WritingSessionMetadataResponse {

    private String documentId;
    private Long metadataId;
    private String mode;
    private String studyStage;
    private String titleSnapshot;
    private String topicTitle;
    private String promptText;
    private String genre;
    private String sourceType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Long examMetadataId;
    private String examType;
    private String taskType;
    private Integer minWords;
    private Integer recommendedMaxWords;
    private Integer maxScore;

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Long getMetadataId() {
        return metadataId;
    }

    public void setMetadataId(Long metadataId) {
        this.metadataId = metadataId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public void setTitleSnapshot(String titleSnapshot) {
        this.titleSnapshot = titleSnapshot;
    }

    public String getTopicTitle() {
        return topicTitle;
    }

    public void setTopicTitle(String topicTitle) {
        this.topicTitle = topicTitle;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getExamMetadataId() {
        return examMetadataId;
    }

    public void setExamMetadataId(Long examMetadataId) {
        this.examMetadataId = examMetadataId;
    }

    public String getExamType() {
        return examType;
    }

    public void setExamType(String examType) {
        this.examType = examType;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getMinWords() {
        return minWords;
    }

    public void setMinWords(Integer minWords) {
        this.minWords = minWords;
    }

    public Integer getRecommendedMaxWords() {
        return recommendedMaxWords;
    }

    public void setRecommendedMaxWords(Integer recommendedMaxWords) {
        this.recommendedMaxWords = recommendedMaxWords;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }
}
