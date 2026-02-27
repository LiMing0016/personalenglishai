package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/writing/evaluate 请求体
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingEvaluateRequest {

    /** 作文正文，必填 */
    @NotBlank(message = "essay is required")
    private String essay;

    /** 给 AI 的补充说明，可选 */
    private String aiHint;

    /** free | guided，默认 free */
    private String mode = "free";

    /** 语言，默认 en */
    private String lang = "en";

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
}
