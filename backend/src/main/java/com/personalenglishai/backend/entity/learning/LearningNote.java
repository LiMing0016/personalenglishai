package com.personalenglishai.backend.entity.learning;

import java.time.LocalDateTime;

public class LearningNote {
    private Long id;
    private String noteUid;
    private Long userId;
    private String type;
    private String title;
    private String contentMarkdown;
    private String structuredPayload;
    private String sourceConversationUid;
    private String sourceMessageUid;
    private String sourceText;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNoteUid() {
        return noteUid;
    }

    public void setNoteUid(String noteUid) {
        this.noteUid = noteUid;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public String getSourceConversationUid() {
        return sourceConversationUid;
    }

    public void setSourceConversationUid(String sourceConversationUid) {
        this.sourceConversationUid = sourceConversationUid;
    }

    public String getSourceMessageUid() {
        return sourceMessageUid;
    }

    public void setSourceMessageUid(String sourceMessageUid) {
        this.sourceMessageUid = sourceMessageUid;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
