package com.personalenglishai.backend.dto;

/**
 * AI 生成响应
 */
public class AiGenerateResponse {
    private String content; // AI 生成的内容

    public AiGenerateResponse() {
    }

    public AiGenerateResponse(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}


