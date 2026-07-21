package com.personalenglishai.backend.controller.dto.assistant;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class AssistantStreamEventResponse {
    private String type;
    private String runId;
    private String traceId;
    private String agentName;
    private String model;
    private String fromAgent;
    private String toAgent;
    private String messageId;
    private String role;
    private String delta;
    private String content;
    private JsonNode parts;
    private AssistantErrorPayload error;
    private Map<String, Object> usage;
    private Map<String, Object> openai;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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

    public String getFromAgent() {
        return fromAgent;
    }

    public void setFromAgent(String fromAgent) {
        this.fromAgent = fromAgent;
    }

    public String getToAgent() {
        return toAgent;
    }

    public void setToAgent(String toAgent) {
        this.toAgent = toAgent;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDelta() {
        return delta;
    }

    public void setDelta(String delta) {
        this.delta = delta;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public JsonNode getParts() {
        return parts;
    }

    public void setParts(JsonNode parts) {
        this.parts = parts;
    }

    public AssistantErrorPayload getError() {
        return error;
    }

    public void setError(AssistantErrorPayload error) {
        this.error = error;
    }

    public Map<String, Object> getUsage() {
        return usage;
    }

    public void setUsage(Map<String, Object> usage) {
        this.usage = usage;
    }

    public Map<String, Object> getOpenai() {
        return openai;
    }

    public void setOpenai(Map<String, Object> openai) {
        this.openai = openai;
    }

    public static class AssistantErrorPayload {
        private String code;
        private String message;
        private Object details;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getDetails() {
            return details;
        }

        public void setDetails(Object details) {
            this.details = details;
        }
    }
}
