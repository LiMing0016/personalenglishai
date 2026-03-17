package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;

public class TrustedRewriteClearRequest {

    @NotBlank(message = "docId is required")
    private String docId;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }
}
