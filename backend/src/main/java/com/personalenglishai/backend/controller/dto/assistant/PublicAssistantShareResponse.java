package com.personalenglishai.backend.controller.dto.assistant;

import java.time.LocalDateTime;
import java.util.List;

public class PublicAssistantShareResponse {
    private String title;
    private List<AssistantMessageResponse> messages;
    private LocalDateTime createdAt;

    public PublicAssistantShareResponse(String title, List<AssistantMessageResponse> messages, LocalDateTime createdAt) {
        this.title = title;
        this.messages = messages;
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public List<AssistantMessageResponse> getMessages() {
        return messages;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
