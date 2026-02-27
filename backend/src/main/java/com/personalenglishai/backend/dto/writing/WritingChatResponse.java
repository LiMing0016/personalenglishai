package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/writing/chat 响应体
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingChatResponse {

    private String requestId;
    private String assistantMessage;
    private RewriteDto rewrite;
    /** 选区改写结果，用于「替换选中内容」 */
    private String resultText;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public RewriteDto getRewrite() {
        return rewrite;
    }

    public void setRewrite(RewriteDto rewrite) {
        this.rewrite = rewrite;
    }

    public static class RewriteDto {
        private String fullText;
        private String summary;

        public String getFullText() {
            return fullText;
        }

        public void setFullText(String fullText) {
            this.fullText = fullText;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }
}
