package com.personalenglishai.backend.entity.subscription;

import java.time.LocalDateTime;

public class UserAiTokenUsageMonthly {
    private Long userId;
    private String usageMonth;
    private Long tokenUsed;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsageMonth() {
        return usageMonth;
    }

    public void setUsageMonth(String usageMonth) {
        this.usageMonth = usageMonth;
    }

    public Long getTokenUsed() {
        return tokenUsed;
    }

    public void setTokenUsed(Long tokenUsed) {
        this.tokenUsed = tokenUsed;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
