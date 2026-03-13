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
    private String studyStage;
    private String rubricKey;
    private String examPolicyKey;
    private String modelVersion;
    private Integer evaluatedRevision;
    private String examBandLabel;
    private Integer examBandMin;
    private Integer examBandMax;
    private String directionRelevance;
    private String directionTaskCompletion;
    private String directionCoverage;
    private String directionMaxBand;
    private Integer capScore;
    private Integer deductionTotal;
    private String penaltyFlagsJson;
    private String directionReasonsJson;
    private String adjustmentReasonsJson;
    private Integer wordCount;
    private Integer sentenceCount;
    private Integer paragraphCount;
    private Integer totalErrorCount;
    private Integer majorErrorCount;
    private Integer minorErrorCount;
    private Integer contentQuality;
    private Integer taskAchievement;
    private Integer structureScore;
    private Integer vocabularyScore;
    private Integer grammarScore;
    private Integer expressionScore;
    private Integer grammarErrorCount;
    private Integer spellingErrorCount;
    private Integer vocabularyErrorCount;
    private Integer lexicalErrorCount;
    private Integer punctuationErrorCount;
    private Integer syntaxErrorCount;
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

    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }

    public String getRubricKey() { return rubricKey; }
    public void setRubricKey(String rubricKey) { this.rubricKey = rubricKey; }

    public String getExamPolicyKey() { return examPolicyKey; }
    public void setExamPolicyKey(String examPolicyKey) { this.examPolicyKey = examPolicyKey; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public Integer getEvaluatedRevision() { return evaluatedRevision; }
    public void setEvaluatedRevision(Integer evaluatedRevision) { this.evaluatedRevision = evaluatedRevision; }

    public String getExamBandLabel() { return examBandLabel; }
    public void setExamBandLabel(String examBandLabel) { this.examBandLabel = examBandLabel; }

    public Integer getExamBandMin() { return examBandMin; }
    public void setExamBandMin(Integer examBandMin) { this.examBandMin = examBandMin; }

    public Integer getExamBandMax() { return examBandMax; }
    public void setExamBandMax(Integer examBandMax) { this.examBandMax = examBandMax; }

    public String getDirectionRelevance() { return directionRelevance; }
    public void setDirectionRelevance(String directionRelevance) { this.directionRelevance = directionRelevance; }

    public String getDirectionTaskCompletion() { return directionTaskCompletion; }
    public void setDirectionTaskCompletion(String directionTaskCompletion) { this.directionTaskCompletion = directionTaskCompletion; }

    public String getDirectionCoverage() { return directionCoverage; }
    public void setDirectionCoverage(String directionCoverage) { this.directionCoverage = directionCoverage; }

    public String getDirectionMaxBand() { return directionMaxBand; }
    public void setDirectionMaxBand(String directionMaxBand) { this.directionMaxBand = directionMaxBand; }

    public Integer getCapScore() { return capScore; }
    public void setCapScore(Integer capScore) { this.capScore = capScore; }

    public Integer getDeductionTotal() { return deductionTotal; }
    public void setDeductionTotal(Integer deductionTotal) { this.deductionTotal = deductionTotal; }

    public String getPenaltyFlagsJson() { return penaltyFlagsJson; }
    public void setPenaltyFlagsJson(String penaltyFlagsJson) { this.penaltyFlagsJson = penaltyFlagsJson; }

    public String getDirectionReasonsJson() { return directionReasonsJson; }
    public void setDirectionReasonsJson(String directionReasonsJson) { this.directionReasonsJson = directionReasonsJson; }

    public String getAdjustmentReasonsJson() { return adjustmentReasonsJson; }
    public void setAdjustmentReasonsJson(String adjustmentReasonsJson) { this.adjustmentReasonsJson = adjustmentReasonsJson; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Integer getSentenceCount() { return sentenceCount; }
    public void setSentenceCount(Integer sentenceCount) { this.sentenceCount = sentenceCount; }

    public Integer getParagraphCount() { return paragraphCount; }
    public void setParagraphCount(Integer paragraphCount) { this.paragraphCount = paragraphCount; }

    public Integer getTotalErrorCount() { return totalErrorCount; }
    public void setTotalErrorCount(Integer totalErrorCount) { this.totalErrorCount = totalErrorCount; }

    public Integer getMajorErrorCount() { return majorErrorCount; }
    public void setMajorErrorCount(Integer majorErrorCount) { this.majorErrorCount = majorErrorCount; }

    public Integer getMinorErrorCount() { return minorErrorCount; }
    public void setMinorErrorCount(Integer minorErrorCount) { this.minorErrorCount = minorErrorCount; }

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

    public Integer getLexicalErrorCount() { return lexicalErrorCount; }
    public void setLexicalErrorCount(Integer lexicalErrorCount) { this.lexicalErrorCount = lexicalErrorCount; }

    public Integer getPunctuationErrorCount() { return punctuationErrorCount; }
    public void setPunctuationErrorCount(Integer punctuationErrorCount) { this.punctuationErrorCount = punctuationErrorCount; }

    public Integer getSyntaxErrorCount() { return syntaxErrorCount; }
    public void setSyntaxErrorCount(Integer syntaxErrorCount) { this.syntaxErrorCount = syntaxErrorCount; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
