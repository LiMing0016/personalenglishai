package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/writing/chat 请求体（改写指令）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingChatRequest {

    @NotBlank(message = "essay is required")
    private String essay;

    @NotBlank(message = "instruction is required")
    private String instruction;

    private String lang = "en";
    private String mode = "free";
    private String aiHint;
    /** 选中的文本，作为提问上下文（可选） */
    private String selectedText;

    public String getEssay() {
        return essay;
    }

    public void setEssay(String essay) {
        this.essay = essay;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAiHint() {
        return aiHint;
    }

    public void setAiHint(String aiHint) {
        this.aiHint = aiHint;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public void setSelectedText(String selectedText) {
        this.selectedText = selectedText;
    }
}
