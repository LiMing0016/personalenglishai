package com.personalenglishai.backend.controller.dto.assistant;

import java.time.LocalDateTime;

public class AssistantMessageResponse {
    private String id;
    private String role;
    private String content;
    private String status;
    private LocalDateTime createdAt;

    public AssistantMessageResponse() {
    }

    public AssistantMessageResponse(String id, String role, String content, String status, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
