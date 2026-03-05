package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GrammarCheckRequest {

    @NotBlank(message = "text must not be blank")
    @Size(max = 5000, message = "text must not exceed 5000 characters")
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
