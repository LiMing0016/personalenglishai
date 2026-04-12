package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecognizeHandwritingImageResponse {

    private String imageUrl;
    private String recognizedText;
    private String normalizedText;
    private BigDecimal confidence;

    public RecognizeHandwritingImageResponse() {
    }

    public RecognizeHandwritingImageResponse(String imageUrl, String recognizedText,
                                             String normalizedText, BigDecimal confidence) {
        this.imageUrl = imageUrl;
        this.recognizedText = recognizedText;
        this.normalizedText = normalizedText;
        this.confidence = confidence;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getRecognizedText() {
        return recognizedText;
    }

    public void setRecognizedText(String recognizedText) {
        this.recognizedText = recognizedText;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public void setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }
}
