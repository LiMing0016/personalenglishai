package com.personalenglishai.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class MockSubscriptionPurchaseRequest {
    @NotBlank
    private String planCode;

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}
