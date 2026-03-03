package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishRequest {

    @NotBlank
    private String original;

    private String context;

    private String reason;

    @NotBlank
    private String tier; // basic | steady | advanced | perfect

    @JsonIgnore
    private Long userId;

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
