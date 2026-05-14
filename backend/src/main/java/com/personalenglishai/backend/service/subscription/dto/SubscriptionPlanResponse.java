package com.personalenglishai.backend.service.subscription.dto;

public class SubscriptionPlanResponse {
    private String planCode;
    private String name;
    private Long monthlyTokenLimit;
    private Long dailyTokenLimit;
    private String quotaPeriod;
    private Long tokenLimit;

    public SubscriptionPlanResponse() {
    }

    public SubscriptionPlanResponse(String planCode, String name, Long monthlyTokenLimit) {
        this.planCode = planCode;
        this.name = name;
        this.monthlyTokenLimit = monthlyTokenLimit;
    }

    public SubscriptionPlanResponse(String planCode, String name, Long monthlyTokenLimit,
                                    Long dailyTokenLimit, String quotaPeriod, Long tokenLimit) {
        this.planCode = planCode;
        this.name = name;
        this.monthlyTokenLimit = monthlyTokenLimit;
        this.dailyTokenLimit = dailyTokenLimit;
        this.quotaPeriod = quotaPeriod;
        this.tokenLimit = tokenLimit;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getMonthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public void setMonthlyTokenLimit(Long monthlyTokenLimit) {
        this.monthlyTokenLimit = monthlyTokenLimit;
    }

    public Long getDailyTokenLimit() {
        return dailyTokenLimit;
    }

    public void setDailyTokenLimit(Long dailyTokenLimit) {
        this.dailyTokenLimit = dailyTokenLimit;
    }

    public String getQuotaPeriod() {
        return quotaPeriod;
    }

    public void setQuotaPeriod(String quotaPeriod) {
        this.quotaPeriod = quotaPeriod;
    }

    public Long getTokenLimit() {
        return tokenLimit;
    }

    public void setTokenLimit(Long tokenLimit) {
        this.tokenLimit = tokenLimit;
    }
}
