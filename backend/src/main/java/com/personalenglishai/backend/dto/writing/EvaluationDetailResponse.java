package com.personalenglishai.backend.dto.writing;

public class EvaluationDetailResponse {

    private String essayText;
    private WritingEvaluateResponse result;

    public EvaluationDetailResponse() {
    }

    public EvaluationDetailResponse(String essayText, WritingEvaluateResponse result) {
        this.essayText = essayText;
        this.result = result;
    }

    public String getEssayText() {
        return essayText;
    }

    public void setEssayText(String essayText) {
        this.essayText = essayText;
    }

    public WritingEvaluateResponse getResult() {
        return result;
    }

    public void setResult(WritingEvaluateResponse result) {
        this.result = result;
    }
}
