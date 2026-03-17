package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RewriteApplyResponse {

    private boolean trusted;
    @JsonProperty("hard_error_count")
    private int hardErrorCount;
    private String message;
    private TrustedRewriteSegmentDto record;

    public RewriteApplyResponse() {
    }

    public RewriteApplyResponse(boolean trusted, int hardErrorCount, String message, TrustedRewriteSegmentDto record) {
        this.trusted = trusted;
        this.hardErrorCount = hardErrorCount;
        this.message = message;
        this.record = record;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public void setTrusted(boolean trusted) {
        this.trusted = trusted;
    }

    public int getHardErrorCount() {
        return hardErrorCount;
    }

    public void setHardErrorCount(int hardErrorCount) {
        this.hardErrorCount = hardErrorCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public TrustedRewriteSegmentDto getRecord() {
        return record;
    }

    public void setRecord(TrustedRewriteSegmentDto record) {
        this.record = record;
    }
}
