package com.personalenglishai.backend.controller.dto;

import java.math.BigDecimal;

public class AbilityProfileResponse {
    private BigDecimal taskScore;
    private BigDecimal coherenceScore;
    private BigDecimal grammarScore;
    private BigDecimal vocabularyScore;
    private BigDecimal structureScore;
    private BigDecimal varietyScore;
    private BigDecimal assessedScore;
    private BigDecimal confidence;
    private Integer sampleCount;
    private String updatedAt;

    public BigDecimal getTaskScore() { return taskScore; }
    public void setTaskScore(BigDecimal taskScore) { this.taskScore = taskScore; }
    public BigDecimal getCoherenceScore() { return coherenceScore; }
    public void setCoherenceScore(BigDecimal coherenceScore) { this.coherenceScore = coherenceScore; }
    public BigDecimal getGrammarScore() { return grammarScore; }
    public void setGrammarScore(BigDecimal grammarScore) { this.grammarScore = grammarScore; }
    public BigDecimal getVocabularyScore() { return vocabularyScore; }
    public void setVocabularyScore(BigDecimal vocabularyScore) { this.vocabularyScore = vocabularyScore; }
    public BigDecimal getStructureScore() { return structureScore; }
    public void setStructureScore(BigDecimal structureScore) { this.structureScore = structureScore; }
    public BigDecimal getVarietyScore() { return varietyScore; }
    public void setVarietyScore(BigDecimal varietyScore) { this.varietyScore = varietyScore; }
    public BigDecimal getAssessedScore() { return assessedScore; }
    public void setAssessedScore(BigDecimal assessedScore) { this.assessedScore = assessedScore; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
