package com.personalenglishai.backend.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class RedeemSubscriptionRequest {
    @NotBlank
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
