package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class EssayEvaluation {

    private Long id;
    private Long userId;
    private Long documentId;
    private String mode;
    private String taskPrompt;
    private String essayText;
    private Integer gaokaoScore;
    private Integer maxScore;
    private String band;
    private Integer overallScore;
    private Integer contentQuality;
    private Integer taskAchievement;
    private Integer structureScore;
    private Integer vocabularyScore;
    private Integer grammarScore;
    private Integer expressionScore;
    private Integer grammarErrorCount;
    private Integer spellingErrorCount;
    private Integer vocabularyErrorCount;
    private String resultJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getTaskPrompt() { return taskPrompt; }
    public void setTaskPrompt(String taskPrompt) { this.taskPrompt = taskPrompt; }

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

    public Integer getContentQuality() { return contentQuality; }
    public void setContentQuality(Integer contentQuality) { this.contentQuality = contentQuality; }

    public Integer getTaskAchievement() { return taskAchievement; }
    public void setTaskAchievement(Integer taskAchievement) { this.taskAchievement = taskAchievement; }

    public Integer getStructureScore() { return structureScore; }
    public void setStructureScore(Integer structureScore) { this.structureScore = structureScore; }

    public Integer getVocabularyScore() { return vocabularyScore; }
    public void setVocabularyScore(Integer vocabularyScore) { this.vocabularyScore = vocabularyScore; }

    public Integer getGrammarScore() { return grammarScore; }
    public void setGrammarScore(Integer grammarScore) { this.grammarScore = grammarScore; }

    public Integer getExpressionScore() { return expressionScore; }
    public void setExpressionScore(Integer expressionScore) { this.expressionScore = expressionScore; }

    public Integer getGrammarErrorCount() { return grammarErrorCount; }
    public void setGrammarErrorCount(Integer grammarErrorCount) { this.grammarErrorCount = grammarErrorCount; }

    public Integer getSpellingErrorCount() { return spellingErrorCount; }
    public void setSpellingErrorCount(Integer spellingErrorCount) { this.spellingErrorCount = spellingErrorCount; }

    public Integer getVocabularyErrorCount() { return vocabularyErrorCount; }
    public void setVocabularyErrorCount(Integer vocabularyErrorCount) { this.vocabularyErrorCount = vocabularyErrorCount; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
