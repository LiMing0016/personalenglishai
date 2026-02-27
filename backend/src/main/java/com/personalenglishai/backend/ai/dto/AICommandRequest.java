package com.personalenglishai.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request contract for POST /api/ai/command.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AICommandRequest {

    private Integer apiVersion = 1;

    @NotBlank(message = "intent is required")
    private String intent;

    /** sm | md | lg */
    private String mode;

    /** Natural language instruction. Required for generate. */
    private String instruction;

    @Valid
    private ContextRefs contextRefs;

    private Map<String, Object> constraints;

    private OutputOptions output;

    public Integer getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(Integer apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public ContextRefs getContextRefs() {
        return contextRefs;
    }

    public void setContextRefs(ContextRefs contextRefs) {
        this.contextRefs = contextRefs;
    }

    public Map<String, Object> getConstraints() {
        return constraints;
    }

    public void setConstraints(Map<String, Object> constraints) {
        this.constraints = constraints;
    }

    public OutputOptions getOutput() {
        return output;
    }

    public void setOutput(OutputOptions output) {
        this.output = output;
    }
}
