package com.personalenglishai.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiResult {

    private String format = "markdown";
    private String apply;
    private List<String> explain = new ArrayList<>();

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getApply() {
        return apply;
    }

    public void setApply(String apply) {
        this.apply = apply;
    }

    public List<String> getExplain() {
        return explain;
    }

    public void setExplain(List<String> explain) {
        this.explain = explain;
    }
}
