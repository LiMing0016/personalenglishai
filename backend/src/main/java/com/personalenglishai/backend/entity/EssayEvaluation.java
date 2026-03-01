package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class EssayEvaluation {

    private Long id;
    private Long userId;
    private String mode;
    private String essayText;
    private Integer gaokaoScore;
    private Integer maxScore;
    private String band;
    private Integer overallScore;
    private String resultJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getEssayText() { return essayText; }
    public void setEssayText(String essayText) { this.essayText = essayText; }

    public Integer getGaokaoScore() { return gaokaoScore; }
    public void setGaokaoScore(Integer gaokaoScore) { this.gaokaoScore = gaokaoScore; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public String getBand() { return band; }
    public void setBand(String band) { this.band = band; }

    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
