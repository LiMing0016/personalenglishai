package com.personalenglishai.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 用量信息（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageInfo {

    private Integer inputTokens;
    private Integer outputTokens;
    private Double cost;

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

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }
}
