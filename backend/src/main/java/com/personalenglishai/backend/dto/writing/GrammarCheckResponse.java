package com.personalenglishai.backend.dto.writing;

import java.util.List;

public class GrammarCheckResponse {

    private List<WritingEvaluateResponse.ErrorDto> errors;

    public GrammarCheckResponse() {
    }

    public GrammarCheckResponse(List<WritingEvaluateResponse.ErrorDto> errors) {
        this.errors = errors;
    }

    public List<WritingEvaluateResponse.ErrorDto> getErrors() {
        return errors;
    }

    public void setErrors(List<WritingEvaluateResponse.ErrorDto> errors) {
        this.errors = errors;
    }
}
