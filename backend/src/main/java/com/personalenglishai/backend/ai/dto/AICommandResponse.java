package com.personalenglishai.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
}
