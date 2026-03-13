package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/writing/evaluate request body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingEvaluateRequest {

    @NotBlank(message = "essay is required")
    private String essay;

    private String aiHint;

    /** free | exam */
    private String mode = "free";

    private String lang = "en";

    /** Optional exam task prompt/instruction. */
    private String taskPrompt;


    private String studyStage;
    private String topicTitle;
    private String genre;
    private String examType;
    private String taskType;
    private Integer minWords;
    private Integer recommendedMaxWords;
    private Integer maxScore;
    /** Optional document public_id for binding evaluations to a document. */
    private String documentId;

    /** 由 Controller 从 JWT 注入，不对外暴露 */
    @JsonIgnore
    private Long userId;

    public WritingEvaluateRequest() {
    }

    public String getEssay() {
        return essay;
    }

    public void setEssay(String essay) {
        this.essay = essay;
    }

    public String getAiHint() {
        return aiHint;
    }

    public void setAiHint(String aiHint) {
        this.aiHint = aiHint;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTaskPrompt() {
        return taskPrompt;
    }

    public void setTaskPrompt(String taskPrompt) {
        this.taskPrompt = taskPrompt;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getTopicTitle() {
        return topicTitle;
    }

    public void setTopicTitle(String topicTitle) {
        this.topicTitle = topicTitle;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
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

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

