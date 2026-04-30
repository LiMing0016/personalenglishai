package com.personalenglishai.backend.service.subscription.dto;

public class SubscriptionPlanResponse {
    private String planCode;
    private String name;
    private Long monthlyTokenLimit;

    public SubscriptionPlanResponse() {
    }

    public SubscriptionPlanResponse(String planCode, String name, Long monthlyTokenLimit) {
        this.planCode = planCode;
        this.name = name;
        this.monthlyTokenLimit = monthlyTokenLimit;
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
}
