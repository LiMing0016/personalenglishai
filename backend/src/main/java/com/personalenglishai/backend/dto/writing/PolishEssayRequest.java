package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishEssayRequest {

    @NotBlank
    private String text;

    @NotBlank
    private String tier; // basic | steady | advanced | perfect

    private String studyStage;
    private String writingMode;
    private String topicContent;
    private String taskPrompt;
    private String taskType;
    private Integer minWords;
    private Integer recommendedMaxWords;

    @JsonIgnore
    private Long userId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }

    public String getWritingMode() { return writingMode; }
    public void setWritingMode(String writingMode) { this.writingMode = writingMode; }

    public String getTopicContent() { return topicContent; }
    public void setTopicContent(String topicContent) { this.topicContent = topicContent; }

    public String getTaskPrompt() { return taskPrompt; }
    public void setTaskPrompt(String taskPrompt) { this.taskPrompt = taskPrompt; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public Integer getMinWords() { return minWords; }
    public void setMinWords(Integer minWords) { this.minWords = minWords; }

    public Integer getRecommendedMaxWords() { return recommendedMaxWords; }
    public void setRecommendedMaxWords(Integer recommendedMaxWords) { this.recommendedMaxWords = recommendedMaxWords; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
