package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingModelEssayRequest {

    @NotBlank
    private String essay;

    private String studyStage;
    private String writingMode;
    private String taskType;
    private String topicContent;
    private String taskPrompt;
    private Integer minWords;
    private Integer recommendedMaxWords;

    @JsonIgnore
    private Long userId;

    public String getEssay() {
        return essay;
    }

    public void setEssay(String essay) {
        this.essay = essay;
    }

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getWritingMode() {
        return writingMode;
    }

    public void setWritingMode(String writingMode) {
        this.writingMode = writingMode;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTopicContent() {
        return topicContent;
    }

    public void setTopicContent(String topicContent) {
        this.topicContent = topicContent;
    }

    public String getTaskPrompt() {
        return taskPrompt;
    }

    public void setTaskPrompt(String taskPrompt) {
        this.taskPrompt = taskPrompt;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
