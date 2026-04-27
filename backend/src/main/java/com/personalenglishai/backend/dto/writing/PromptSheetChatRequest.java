package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PromptSheetChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息过长")
    private String message;

    private String studyStage;
    private String taskType;
    private String promptType;
    private String genre;
    private String wordRange;
    private String requirements;
    private String currentTopic;
    private String currentPromptText;
    private Boolean hasCanvas;
    private String aiProvider;
    private Long userId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
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

    public String getCurrentTopic() {
        return currentTopic;
    }

    public void setCurrentTopic(String currentTopic) {
        this.currentTopic = currentTopic;
    }

    public String getCurrentPromptText() {
        return currentPromptText;
    }

    public void setCurrentPromptText(String currentPromptText) {
        this.currentPromptText = currentPromptText;
    }

    public Boolean getHasCanvas() {
        return hasCanvas;
    }

    public void setHasCanvas(Boolean hasCanvas) {
        this.hasCanvas = hasCanvas;
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
