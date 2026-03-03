package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolishResponse {

    private String polished;
    private String explanation;

    public PolishResponse() {}

    public PolishResponse(String polished, String explanation) {
        this.polished = polished;
        this.explanation = explanation;
    }

    public String getPolished() { return polished; }
    public void setPolished(String polished) { this.polished = polished; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
