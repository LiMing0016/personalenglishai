package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class EssayPrompt {

    private Long id;
    private Integer stageId;
    private String paper;
    private String title;
    private String promptText;
    private String source;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getStageId() { return stageId; }
    public void setStageId(Integer stageId) { this.stageId = stageId; }

    public String getPaper() { return paper; }
    public void setPaper(String paper) { this.paper = paper; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPromptText() { return promptText; }
    public void setPromptText(String promptText) { this.promptText = promptText; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
