package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class RubricVersion {

    private Long id;
    private String rubricKey;
    private String stage;
    private Integer isActive;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRubricKey() {
        return rubricKey;
    }

    public void setRubricKey(String rubricKey) {
        this.rubricKey = rubricKey;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

