package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/writing/evaluate request body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingEvaluateRequest {

    @NotBlank(message = "essay is required")
    private String essay;

    private String aiHint;

    /** free | exam */
    private String mode = "free";

    private String lang = "en";

    /** Optional exam task prompt/instruction. */
    private String taskPrompt;

    /** 由 Controller 从 JWT 注入，不对外暴露 */
    @JsonIgnore
    private Long userId;

    public WritingEvaluateRequest() {
    }

    public String getEssay() {
        return essay;
    }

    public void setEssay(String essay) {
        this.essay = essay;
    }

    public String getAiHint() {
        return aiHint;
    }

    public void setAiHint(String aiHint) {
        this.aiHint = aiHint;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTaskPrompt() {
        return taskPrompt;
    }

    public void setTaskPrompt(String taskPrompt) {
        this.taskPrompt = taskPrompt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

