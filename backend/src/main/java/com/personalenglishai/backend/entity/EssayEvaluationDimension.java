package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class EssayEvaluationDimension {

    private Long id;
    private Long evaluationId;
    private String dimensionKey;
    private String dimensionLabelSnapshot;
    private Integer sortOrder;
    private Integer score;
    private String grade;
    private String strength;
    private String weakness;
    private String suggestion;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEvaluationId() { return evaluationId; }
    public void setEvaluationId(Long evaluationId) { this.evaluationId = evaluationId; }

    public String getDimensionKey() { return dimensionKey; }
    public void setDimensionKey(String dimensionKey) { this.dimensionKey = dimensionKey; }

    public String getDimensionLabelSnapshot() { return dimensionLabelSnapshot; }
    public void setDimensionLabelSnapshot(String dimensionLabelSnapshot) { this.dimensionLabelSnapshot = dimensionLabelSnapshot; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getStrength() { return strength; }
    public void setStrength(String strength) { this.strength = strength; }

    public String getWeakness() { return weakness; }
    public void setWeakness(String weakness) { this.weakness = weakness; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
