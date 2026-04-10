package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GenerateExamPromptRequest {

    @NotBlank(message = "原始输入不能为空")
    @Size(max = 4000, message = "原始输入过长")
    private String originalInput;

    @NotBlank(message = "主题不能为空")
    @Size(max = 1000, message = "主题内容过长")
    private String topic;

    private String studyStage;
    private String promptType;
    private String genre;
    private String wordRange;
    private String requirements;
    private String taskType;
    private Integer maxScore;
    private String aiProvider;
    private Long userId;

    public String getOriginalInput() {
        return originalInput;
    }

    public void setOriginalInput(String originalInput) {
        this.originalInput = originalInput;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getPromptType() {
        return promptType;
    }

    public void setPromptType(String promptType) {
        this.promptType = promptType;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getWordRange() {
        return wordRange;
    }

    public void setWordRange(String wordRange) {
        this.wordRange = wordRange;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
