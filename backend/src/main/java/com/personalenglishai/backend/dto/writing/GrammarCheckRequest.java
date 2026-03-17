package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GrammarCheckRequest {

    @NotBlank(message = "text must not be blank")
    @Size(max = 5000, message = "text must not exceed 5000 characters")
    private String text;

    /** Document ID for scoping grammar suppressions. */
    private String docId;

    /** Trinka editing mode: lite -> basic, power -> advanced. */
    private String trinkaMode;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getTrinkaMode() {
        return trinkaMode;
    }

    public void setTrinkaMode(String trinkaMode) {
        this.trinkaMode = trinkaMode;
    }
}
