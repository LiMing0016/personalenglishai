package com.personalenglishai.backend.entity.assistant;

import java.time.LocalDateTime;

public class AssistantArchiveSetting {
    private Long userId;
    private String archiveDir;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getArchiveDir() {
        return archiveDir;
    }

    public void setArchiveDir(String archiveDir) {
        this.archiveDir = archiveDir;
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
}
