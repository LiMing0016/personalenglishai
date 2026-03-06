package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishEssayRequest {

    @NotBlank
    private String text;

    @NotBlank
    private String tier; // basic | steady | advanced | perfect

    @JsonIgnore
    private Long userId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
