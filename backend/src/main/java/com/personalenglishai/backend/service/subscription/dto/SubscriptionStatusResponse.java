package com.personalenglishai.backend.service.subscription.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;

public class SubscriptionStatusResponse {
    private String planCode;
    private String planName;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private String quotaPeriod;
    private LocalDate usageDate;
    private String usageMonth;
    private Long dailyTokenLimit;
    private Long monthlyTokenLimit;
    private Long tokenLimit;
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

    public String getQuotaPeriod() {
        return quotaPeriod;
    }

    public void setQuotaPeriod(String quotaPeriod) {
        this.quotaPeriod = quotaPeriod;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public String getUsageMonth() {
        return usageMonth;
    }

    public void setUsageMonth(String usageMonth) {
        this.usageMonth = usageMonth;
    }

    public Long getDailyTokenLimit() {
        return dailyTokenLimit;
    }

    public void setDailyTokenLimit(Long dailyTokenLimit) {
        this.dailyTokenLimit = dailyTokenLimit;
    }

    public Long getMonthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public void setMonthlyTokenLimit(Long monthlyTokenLimit) {
        this.monthlyTokenLimit = monthlyTokenLimit;
    }

    public Long getTokenLimit() {
        return tokenLimit;
    }

    public void setTokenLimit(Long tokenLimit) {
        this.tokenLimit = tokenLimit;
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
