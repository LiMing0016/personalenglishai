package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class GenerateExamDialogueTurnRequest {

    private String studyStage;
    private String aiProvider;
    private String selectedMode;

    @NotEmpty(message = "消息列表不能为空")
    private List<ExamWorkbenchMessageDto> messages = new ArrayList<>();

    public String getStudyStage() {
        return studyStage;
    }

    public void setStudyStage(String studyStage) {
        this.studyStage = studyStage;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getSelectedMode() {
        return selectedMode;
    }

    public void setSelectedMode(String selectedMode) {
        this.selectedMode = selectedMode;
    }

    public List<ExamWorkbenchMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ExamWorkbenchMessageDto> messages) {
        this.messages = messages;
    }
}
