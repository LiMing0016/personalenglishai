package com.personalenglishai.backend.dto;

/**
 * AI 生成请求
 */
public class AiGenerateRequest {
    private String prompt; // 用户输入的提示词

    public AiGenerateRequest() {
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}


