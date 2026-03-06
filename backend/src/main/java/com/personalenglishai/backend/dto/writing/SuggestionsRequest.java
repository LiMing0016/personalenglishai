package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SuggestionsRequest {

    @NotBlank(message = "text is required")
    @Size(max = 5000, message = "text too long")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
