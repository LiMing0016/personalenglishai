package com.personalenglishai.backend.controller.dto.assistant;

import jakarta.validation.constraints.Size;

public class CreateAssistantConversationRequest {
    @Size(max = 160)
    private String title;

    private Long projectId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
