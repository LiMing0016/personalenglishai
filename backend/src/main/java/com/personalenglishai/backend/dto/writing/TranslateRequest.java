package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranslateRequest {

    @NotBlank
    private String text;

    @NotBlank
    private String mode; // "full" | "detailed"

    @JsonIgnore
    private Long userId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
