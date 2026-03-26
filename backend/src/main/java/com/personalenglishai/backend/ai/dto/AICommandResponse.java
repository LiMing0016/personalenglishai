package com.personalenglishai.backend.ai.dto;

import com.personalenglishai.backend.ai.assistant.AssistantAction;
import com.personalenglishai.backend.ai.assistant.AssistantToolRun;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response for POST /api/ai/command.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AICommandResponse {

    private String traceId;
    /** success | failed | running */
    private String status;
    private AiResult result;

    /** Backward compatibility field. */
    private FinalResult finalResult;
    private String responseId;
    private String message;
    private List<AssistantAction> actions;
    private List<AssistantToolRun> toolRuns;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public AiResult getResult() {
        return result;
    }

    public void setResult(AiResult result) {
        this.result = result;
    }

    public FinalResult getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(FinalResult finalResult) {
        this.finalResult = finalResult;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<AssistantAction> getActions() {
        return actions;
    }

    public void setActions(List<AssistantAction> actions) {
        this.actions = actions;
    }

    public List<AssistantToolRun> getToolRuns() {
        return toolRuns;
    }

    public void setToolRuns(List<AssistantToolRun> toolRuns) {
        this.toolRuns = toolRuns;
    }
}
