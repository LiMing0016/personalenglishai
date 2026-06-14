package com.personalenglishai.backend.dto.translation;

public class TranslationDocumentQualityDto {
    private double documentQualityScore;
    private double textCoverageRatio;
    private double garbledRatio;
    private double locationCoverageRatio;
    private double chunkHighQualityRatio;
    private boolean fallbackRecommended;

    public double getDocumentQualityScore() {
        return documentQualityScore;
    }

    public void setDocumentQualityScore(double documentQualityScore) {
        this.documentQualityScore = documentQualityScore;
    }

    public double getTextCoverageRatio() {
        return textCoverageRatio;
    }

    public void setTextCoverageRatio(double textCoverageRatio) {
        this.textCoverageRatio = textCoverageRatio;
    }

    public double getGarbledRatio() {
        return garbledRatio;
    }

    public void setGarbledRatio(double garbledRatio) {
        this.garbledRatio = garbledRatio;
    }

    public double getLocationCoverageRatio() {
        return locationCoverageRatio;
    }

    public void setLocationCoverageRatio(double locationCoverageRatio) {
        this.locationCoverageRatio = locationCoverageRatio;
    }

    public double getChunkHighQualityRatio() {
        return chunkHighQualityRatio;
    }

    public void setChunkHighQualityRatio(double chunkHighQualityRatio) {
        this.chunkHighQualityRatio = chunkHighQualityRatio;
    }

    public boolean isFallbackRecommended() {
        return fallbackRecommended;
    }

    public void setFallbackRecommended(boolean fallbackRecommended) {
        this.fallbackRecommended = fallbackRecommended;
    }
}
