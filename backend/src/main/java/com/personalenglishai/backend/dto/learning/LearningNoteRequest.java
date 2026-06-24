package com.personalenglishai.backend.dto.learning;

public class LearningNoteRequest {
    private String type;
    private String title;
    private String contentMarkdown;
    private String structuredPayload;
    private String sourceConversationId;
    private String sourceMessageId;
    private String sourceText;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public void setContentMarkdown(String contentMarkdown) {
        this.contentMarkdown = contentMarkdown;
    }

    public String getStructuredPayload() {
        return structuredPayload;
    }

    public void setStructuredPayload(String structuredPayload) {
        this.structuredPayload = structuredPayload;
    }

    public String getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(String sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(String sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }
}
