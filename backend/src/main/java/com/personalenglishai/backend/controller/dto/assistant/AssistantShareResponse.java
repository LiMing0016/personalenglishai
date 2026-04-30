package com.personalenglishai.backend.controller.dto.assistant;

import java.time.LocalDateTime;

public class AssistantShareResponse {
    private String shareToken;
    private String sharePath;
    private LocalDateTime createdAt;

    public AssistantShareResponse(String shareToken, String sharePath, LocalDateTime createdAt) {
        this.shareToken = shareToken;
        this.sharePath = sharePath;
        this.createdAt = createdAt;
    }

    public String getShareToken() {
        return shareToken;
    }

    public String getSharePath() {
        return sharePath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
