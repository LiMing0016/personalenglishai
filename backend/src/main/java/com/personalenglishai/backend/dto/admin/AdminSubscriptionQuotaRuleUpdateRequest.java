package com.personalenglishai.backend.dto.admin;

public class AdminSubscriptionQuotaRuleUpdateRequest {
    private Long dailyTokenLimit;
    private Long monthlyTokenLimit;

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
}
