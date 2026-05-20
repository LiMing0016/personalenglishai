package com.personalenglishai.backend.entity.learning;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LearningEvidence {
    private Long id;
    private String evidenceUid;
    private String candidateUid;
    private Long userId;
    private String evidenceType;
    private String text;
    private BigDecimal score;
    private String signalsJson;
    private String modelJudgementJson;
    private String extractorSourcesJson;
    private String comparisonStatus;
    private String sourceMessageIdsJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEvidenceUid() {
        return evidenceUid;
    }

    public void setEvidenceUid(String evidenceUid) {
        this.evidenceUid = evidenceUid;
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

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getSignalsJson() {
        return signalsJson;
    }

    public void setSignalsJson(String signalsJson) {
        this.signalsJson = signalsJson;
    }

    public String getModelJudgementJson() {
        return modelJudgementJson;
    }

    public void setModelJudgementJson(String modelJudgementJson) {
        this.modelJudgementJson = modelJudgementJson;
    }

    public String getExtractorSourcesJson() {
        return extractorSourcesJson;
    }

    public void setExtractorSourcesJson(String extractorSourcesJson) {
        this.extractorSourcesJson = extractorSourcesJson;
    }

    public String getComparisonStatus() {
        return comparisonStatus;
    }

    public void setComparisonStatus(String comparisonStatus) {
        this.comparisonStatus = comparisonStatus;
    }

    public String getSourceMessageIdsJson() {
        return sourceMessageIdsJson;
    }

    public void setSourceMessageIdsJson(String sourceMessageIdsJson) {
        this.sourceMessageIdsJson = sourceMessageIdsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
