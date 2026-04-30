package com.personalenglishai.backend.controller.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendAssistantMessageRequest {
    @NotBlank
    @Size(max = 8000)
    private String message;

    @Size(max = 50)
    private String studyStage;

    @Size(max = 20)
    private String assistantMode;

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

    public String getAssistantMode() {
        return assistantMode;
    }

    public void setAssistantMode(String assistantMode) {
        this.assistantMode = assistantMode;
    }
}
