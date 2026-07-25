package com.personalenglishai.backend.controller.dto.assistant;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public class AssistantMessageResponse {
    private String id;
    private String role;
    private String content;
    private JsonNode parts;
    private String status;
    private LocalDateTime createdAt;

    public AssistantMessageResponse() {
    }

    public AssistantMessageResponse(String id, String role, String content, String status, LocalDateTime createdAt) {
        this(id, role, content, null, status, createdAt);
    }

    public AssistantMessageResponse(
            String id,
            String role,
            String content,
            JsonNode parts,
            String status,
            LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.parts = parts;
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

    public JsonNode getParts() {
        return parts;
    }

    public void setParts(JsonNode parts) {
        this.parts = parts;
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
