package com.personalenglishai.backend.controller.dto.assistant;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChatKitSessionRequest {
    private String workflowId;
    private String conversationId;
    private Map<String, Object> writingContext = new LinkedHashMap<>();
    private Map<String, Object> stateVariables = new LinkedHashMap<>();

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getWritingContext() {
        return writingContext;
    }

    public void setWritingContext(Map<String, Object> writingContext) {
        this.writingContext = writingContext == null ? new LinkedHashMap<>() : writingContext;
    }

    public Map<String, Object> getStateVariables() {
        return stateVariables;
    }

    public void setStateVariables(Map<String, Object> stateVariables) {
        this.stateVariables = stateVariables == null ? new LinkedHashMap<>() : stateVariables;
    }
}
