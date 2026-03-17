package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RewriteApplyRequest {

    @NotBlank(message = "docId is required")
    private String docId;

    @NotBlank(message = "essay is required")
    private String essay;

    @NotNull(message = "start is required")
    private Integer start;

    @NotNull(message = "end is required")
    private Integer end;

    @NotBlank(message = "original is required")
    private String original;

    @NotBlank(message = "replacement is required")
    private String replacement;

    @NotBlank(message = "tier is required")
    private String tier;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getEssay() {
        return essay;
    }

    public void setEssay(String essay) {
        this.essay = essay;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getEnd() {
        return end;
    }

    public void setEnd(Integer end) {
        this.end = end;
    }

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
