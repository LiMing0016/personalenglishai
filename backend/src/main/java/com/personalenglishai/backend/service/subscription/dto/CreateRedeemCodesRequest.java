package com.personalenglishai.backend.service.subscription.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class CreateRedeemCodesRequest {
    @NotBlank
    private String planCode;
    @NotNull
    @Min(1)
    @Max(3650)
    private Integer durationDays;
    @NotNull
    @Min(1)
    @Max(500)
    private Integer count;
    private LocalDateTime expiresAt;
    private String batchName;

    public CreateRedeemCodesRequest() {}

    public CreateRedeemCodesRequest(String planCode, Integer durationDays, Integer count, LocalDateTime expiresAt, String batchName) {
        this.planCode = planCode;
        this.durationDays = durationDays;
        this.count = count;
        this.expiresAt = expiresAt;
        this.batchName = batchName;
    }

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getBatchName() { return batchName; }
    public void setBatchName(String batchName) { this.batchName = batchName; }
}
