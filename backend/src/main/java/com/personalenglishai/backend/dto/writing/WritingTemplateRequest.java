package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingTemplateRequest {

    @NotBlank
    private String text;

    private String taskPrompt;
    private String studyStage;
    private String writingMode;

    @JsonIgnore
    private Long userId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getTaskPrompt() { return taskPrompt; }
    public void setTaskPrompt(String taskPrompt) { this.taskPrompt = taskPrompt; }

    public String getStudyStage() { return studyStage; }
    public void setStudyStage(String studyStage) { this.studyStage = studyStage; }

    public String getWritingMode() { return writingMode; }
    public void setWritingMode(String writingMode) { this.writingMode = writingMode; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
