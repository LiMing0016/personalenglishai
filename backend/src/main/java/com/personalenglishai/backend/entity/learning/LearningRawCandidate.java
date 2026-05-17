package com.personalenglishai.backend.entity.learning;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LearningRawCandidate {
    private Long id;
    private String candidateUid;
    private Long userId;
    private String conversationUid;
    private String messageUid;
    private String sourceRole;
    private String candidateType;
    private String text;
    private String normalizedText;
    private String extractorType;
    private String extractionRunUid;
    private String sourceExcerpt;
    private String sourceHeading;
    private String localSignalsJson;
    private String localFeatureJson;
    private BigDecimal modelConfidence;
    private String comparisonStatus;
    private BigDecimal localPrefilterScore;
    private BigDecimal embeddingScore;
    private BigDecimal judgeScore;
    private BigDecimal finalCandidateScore;
    private Integer occurrenceCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCandidateUid() {
        return candidateUid;
    }

    public void setCandidateUid(String candidateUid) {
        this.candidateUid = candidateUid;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getConversationUid() {
        return conversationUid;
    }

    public void setConversationUid(String conversationUid) {
        this.conversationUid = conversationUid;
    }

    public String getMessageUid() {
        return messageUid;
    }

    public void setMessageUid(String messageUid) {
        this.messageUid = messageUid;
    }

    public String getSourceRole() {
        return sourceRole;
    }

    public void setSourceRole(String sourceRole) {
        this.sourceRole = sourceRole;
    }

    public String getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(String candidateType) {
        this.candidateType = candidateType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public void setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public String getExtractorType() {
        return extractorType;
    }

    public void setExtractorType(String extractorType) {
        this.extractorType = extractorType;
    }

    public String getExtractionRunUid() {
        return extractionRunUid;
    }

    public void setExtractionRunUid(String extractionRunUid) {
        this.extractionRunUid = extractionRunUid;
    }

    public String getSourceExcerpt() {
        return sourceExcerpt;
    }

    public void setSourceExcerpt(String sourceExcerpt) {
        this.sourceExcerpt = sourceExcerpt;
    }

    public String getSourceHeading() {
        return sourceHeading;
    }

    public void setSourceHeading(String sourceHeading) {
        this.sourceHeading = sourceHeading;
    }

    public String getLocalSignalsJson() {
        return localSignalsJson;
    }

    public void setLocalSignalsJson(String localSignalsJson) {
        this.localSignalsJson = localSignalsJson;
    }

    public String getLocalFeatureJson() {
        return localFeatureJson;
    }

    public void setLocalFeatureJson(String localFeatureJson) {
        this.localFeatureJson = localFeatureJson;
    }

    public BigDecimal getModelConfidence() {
        return modelConfidence;
    }

    public void setModelConfidence(BigDecimal modelConfidence) {
        this.modelConfidence = modelConfidence;
    }

    public String getComparisonStatus() {
        return comparisonStatus;
    }

    public void setComparisonStatus(String comparisonStatus) {
        this.comparisonStatus = comparisonStatus;
    }

    public BigDecimal getLocalPrefilterScore() {
        return localPrefilterScore;
    }

    public void setLocalPrefilterScore(BigDecimal localPrefilterScore) {
        this.localPrefilterScore = localPrefilterScore;
    }

    public BigDecimal getEmbeddingScore() {
        return embeddingScore;
    }

    public void setEmbeddingScore(BigDecimal embeddingScore) {
        this.embeddingScore = embeddingScore;
    }

    public BigDecimal getJudgeScore() {
        return judgeScore;
    }

    public void setJudgeScore(BigDecimal judgeScore) {
        this.judgeScore = judgeScore;
    }

    public BigDecimal getFinalCandidateScore() {
        return finalCandidateScore;
    }

    public void setFinalCandidateScore(BigDecimal finalCandidateScore) {
        this.finalCandidateScore = finalCandidateScore;
    }

    public Integer getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(Integer occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public LocalDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(LocalDateTime firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
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
}
