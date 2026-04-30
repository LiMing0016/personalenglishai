package com.personalenglishai.backend.controller.dto.assistant;

import java.time.LocalDateTime;

public class AssistantConversationSummaryResponse {
    private String id;
    private Long projectId;
    private String title;
    private String summary;
    private boolean pinned;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AssistantConversationSummaryResponse(
            String id,
            Long projectId,
            String title,
            String summary,
            boolean pinned,
            boolean archived,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.title = title;
        this.summary = summary;
        this.pinned = pinned;
        this.archived = archived;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
