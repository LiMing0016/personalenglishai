package com.personalenglishai.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 最终结果：content 必填，diff/usage 可选
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinalResult {

    private String content;
    /** 预留给编辑器 diff */
    private Map<String, Object> diff;
    private UsageInfo usage;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getDiff() {
        return diff;
    }

    public void setDiff(Map<String, Object> diff) {
        this.diff = diff;
    }

    public UsageInfo getUsage() {
        return usage;
    }

    public void setUsage(UsageInfo usage) {
        this.usage = usage;
    }
}
