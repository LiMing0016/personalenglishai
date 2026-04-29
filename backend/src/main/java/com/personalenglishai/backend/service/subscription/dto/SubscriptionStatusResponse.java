package com.personalenglishai.backend.service.subscription.dto;

import java.time.LocalDateTime;

public class SubscriptionStatusResponse {
    private String planCode;
    private String planName;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private String usageMonth;
    private Long monthlyTokenLimit;
    private Long tokenUsed;
    private Long tokenRemaining;
    private Boolean overLimit;

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public LocalDateTime getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(LocalDateTime currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public LocalDateTime getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(LocalDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public String getUsageMonth() {
        return usageMonth;
    }

    public void setUsageMonth(String usageMonth) {
        this.usageMonth = usageMonth;
    }

    public Long getMonthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public void setMonthlyTokenLimit(Long monthlyTokenLimit) {
        this.monthlyTokenLimit = monthlyTokenLimit;
    }

    public Long getTokenUsed() {
        return tokenUsed;
    }

    public void setTokenUsed(Long tokenUsed) {
        this.tokenUsed = tokenUsed;
    }

    public Long getTokenRemaining() {
        return tokenRemaining;
    }

    public void setTokenRemaining(Long tokenRemaining) {
        this.tokenRemaining = tokenRemaining;
    }

    public Boolean getOverLimit() {
        return overLimit;
    }

    public void setOverLimit(Boolean overLimit) {
        this.overLimit = overLimit;
    }
}
