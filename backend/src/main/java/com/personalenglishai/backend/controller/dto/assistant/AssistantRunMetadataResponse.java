package com.personalenglishai.backend.controller.dto.assistant;

public class AssistantRunMetadataResponse {
    private String runId;
    private String traceId;
    private String agentName;
    private String model;
    private String mode;
    private String intent;
    private String scope;
    private String finishReason;
    private Usage usage;
    private OpenAiState openai;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public OpenAiState getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiState openai) {
        this.openai = openai;
    }

    public static class Usage {
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private Integer requests;

        public Integer getInputTokens() {
            return inputTokens;
        }

        public void setInputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
        }

        public Integer getOutputTokens() {
            return outputTokens;
        }

        public void setOutputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Integer getRequests() {
            return requests;
        }

        public void setRequests(Integer requests) {
            this.requests = requests;
        }
    }

    public static class OpenAiState {
        private String responseId;
        private String conversationId;
        private String previousResponseId;

        public String getResponseId() {
            return responseId;
        }

        public void setResponseId(String responseId) {
            this.responseId = responseId;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public String getPreviousResponseId() {
            return previousResponseId;
        }

        public void setPreviousResponseId(String previousResponseId) {
            this.previousResponseId = previousResponseId;
        }
    }
}
