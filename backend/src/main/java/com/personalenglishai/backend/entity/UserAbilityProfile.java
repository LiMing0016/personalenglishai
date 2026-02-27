package com.personalenglishai.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserAbilityProfile {

    private Long id;
    private Long userId;
    private Integer stage;

    private BigDecimal taskScore;
    private BigDecimal coherenceScore;
    private BigDecimal grammarScore;
    private BigDecimal vocabularyScore;
    private BigDecimal structureScore;
    private BigDecimal varietyScore;
    private BigDecimal assessedScore;

    private BigDecimal confidence;
    private Integer sampleCount;
    private String modelVersion;
    private String rubricVersion;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public BigDecimal getTaskScore() {
        return taskScore;
    }

    public void setTaskScore(BigDecimal taskScore) {
        this.taskScore = taskScore;
    }

    public BigDecimal getCoherenceScore() {
        return coherenceScore;
    }

    public void setCoherenceScore(BigDecimal coherenceScore) {
        this.coherenceScore = coherenceScore;
    }

    public BigDecimal getGrammarScore() {
        return grammarScore;
    }

    public void setGrammarScore(BigDecimal grammarScore) {
        this.grammarScore = grammarScore;
    }

    public BigDecimal getVocabularyScore() {
        return vocabularyScore;
    }

    public void setVocabularyScore(BigDecimal vocabularyScore) {
        this.vocabularyScore = vocabularyScore;
    }

    public BigDecimal getStructureScore() {
        return structureScore;
    }

    public void setStructureScore(BigDecimal structureScore) {
        this.structureScore = structureScore;
    }

    public BigDecimal getVarietyScore() {
        return varietyScore;
    }

    public void setVarietyScore(BigDecimal varietyScore) {
        this.varietyScore = varietyScore;
    }

    public BigDecimal getAssessedScore() {
        return assessedScore;
    }

    public void setAssessedScore(BigDecimal assessedScore) {
        this.assessedScore = assessedScore;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getRubricVersion() {
        return rubricVersion;
    }

    public void setRubricVersion(String rubricVersion) {
        this.rubricVersion = rubricVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
