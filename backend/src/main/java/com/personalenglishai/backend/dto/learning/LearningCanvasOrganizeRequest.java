package com.personalenglishai.backend.dto.learning;

public class LearningCanvasOrganizeRequest {
    private String type;
    private String title;
    private String selectedText;
    private String contextText;
    private String currentMarkdown;
    private String mode;

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

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }

    public String getContextText() {
        return contextText;
    }

    public void setContextText(String contextText) {
        this.contextText = contextText;
    }

    public String getCurrentMarkdown() {
        return currentMarkdown;
    }

    public void setCurrentMarkdown(String currentMarkdown) {
        this.currentMarkdown = currentMarkdown;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
