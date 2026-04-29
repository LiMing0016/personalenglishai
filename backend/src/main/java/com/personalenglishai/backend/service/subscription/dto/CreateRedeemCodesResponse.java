package com.personalenglishai.backend.service.subscription.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CreateRedeemCodesResponse {
    private String planCode;
    private Integer durationDays;
    private LocalDateTime expiresAt;
    private String batchName;
    private List<RedeemCodeItem> codes = new ArrayList<>();

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getBatchName() { return batchName; }
    public void setBatchName(String batchName) { this.batchName = batchName; }
    public List<RedeemCodeItem> getCodes() { return codes; }
    public void setCodes(List<RedeemCodeItem> codes) { this.codes = codes; }

    public static class RedeemCodeItem {
        private String code;

        public RedeemCodeItem() {}

        public RedeemCodeItem(String code) {
            this.code = code;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
}
