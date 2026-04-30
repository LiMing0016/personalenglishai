package com.personalenglishai.backend.entity.subscription;

import java.time.LocalDateTime;

public class SubscriptionRedeemEvent {
    private Long id;
    private Long redeemCodeId;
    private Long userId;
    private String planCode;
    private Integer durationDays;
    private String beforePlanCode;
    private LocalDateTime beforePeriodEnd;
    private String afterPlanCode;
    private LocalDateTime afterPeriodEnd;
    private String redeemIp;
    private LocalDateTime redeemedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRedeemCodeId() { return redeemCodeId; }
    public void setRedeemCodeId(Long redeemCodeId) { this.redeemCodeId = redeemCodeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public String getBeforePlanCode() { return beforePlanCode; }
    public void setBeforePlanCode(String beforePlanCode) { this.beforePlanCode = beforePlanCode; }
    public LocalDateTime getBeforePeriodEnd() { return beforePeriodEnd; }
    public void setBeforePeriodEnd(LocalDateTime beforePeriodEnd) { this.beforePeriodEnd = beforePeriodEnd; }
    public String getAfterPlanCode() { return afterPlanCode; }
    public void setAfterPlanCode(String afterPlanCode) { this.afterPlanCode = afterPlanCode; }
    public LocalDateTime getAfterPeriodEnd() { return afterPeriodEnd; }
    public void setAfterPeriodEnd(LocalDateTime afterPeriodEnd) { this.afterPeriodEnd = afterPeriodEnd; }
    public String getRedeemIp() { return redeemIp; }
    public void setRedeemIp(String redeemIp) { this.redeemIp = redeemIp; }
    public LocalDateTime getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(LocalDateTime redeemedAt) { this.redeemedAt = redeemedAt; }
}
