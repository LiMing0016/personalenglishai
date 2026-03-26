package com.personalenglishai.backend.ai.englishassistant;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnglishAssistantChatResponse {

    private String conversationId;
    private String responseId;
    private String scope;
    private String taskType;
    private Boolean refused;
    private String refusalReason;
    private Boolean usedDraftContext;
    private String message;
    private List<EnglishAssistantUiAction> actions = new ArrayList<>();

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Boolean getRefused() {
        return refused;
    }

    public void setRefused(Boolean refused) {
        this.refused = refused;
    }

    public String getRefusalReason() {
        return refusalReason;
    }

    public void setRefusalReason(String refusalReason) {
        this.refusalReason = refusalReason;
    }

    public Boolean getUsedDraftContext() {
        return usedDraftContext;
    }

    public void setUsedDraftContext(Boolean usedDraftContext) {
        this.usedDraftContext = usedDraftContext;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<EnglishAssistantUiAction> getActions() {
        return actions;
    }

    public void setActions(List<EnglishAssistantUiAction> actions) {
        this.actions = actions == null ? new ArrayList<>() : actions;
    }
}
