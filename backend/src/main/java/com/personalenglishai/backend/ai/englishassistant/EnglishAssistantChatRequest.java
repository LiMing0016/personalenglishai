package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnglishAssistantChatRequest {

    @NotBlank(message = "conversationId is required")
    private String conversationId;

    @NotBlank(message = "message is required")
    private String message;

    private Boolean useDraftContext;
    private String assignmentText;
    private String selectedText;
    private String draftText;
    private String preferredAction;
    private String studyStage;
    private String writingMode;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getUseDraftContext() {
        return useDraftContext;
    }

    public void setUseDraftContext(Boolean useDraftContext) {
        this.useDraftContext = useDraftContext;
    }

    public String getAssignmentText() {
        return assignmentText;
    }

    public void setAssignmentText(String assignmentText) {
        this.assignmentText = assignmentText;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public String getDraftText() {
        return draftText;
    }

    public void setDraftText(String draftText) {
        this.draftText = draftText;
    }

    public String getPreferredAction() {
        return preferredAction;
    }

    public void setPreferredAction(String preferredAction) {
        this.preferredAction = preferredAction;
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
}
