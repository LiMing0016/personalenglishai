package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationParseDiagnosisDto {
    private String textLayer = "NONE";
    private double textCoverageRatio;
    private double garbledRatio;
    private double headerFooterRatio;
    private List<Integer> imageOnlyPages = new ArrayList<>();
    private boolean ocrRecommended;
    private boolean highQualityProviderRecommended;
    private boolean fallbackRecommended;
    private List<String> warnings = new ArrayList<>();

    public String getTextLayer() {
        return textLayer;
    }

    public void setTextLayer(String textLayer) {
        this.textLayer = textLayer;
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

    public double getHeaderFooterRatio() {
        return headerFooterRatio;
    }

    public void setHeaderFooterRatio(double headerFooterRatio) {
        this.headerFooterRatio = headerFooterRatio;
    }

    public List<Integer> getImageOnlyPages() {
        return imageOnlyPages;
    }

    public void setImageOnlyPages(List<Integer> imageOnlyPages) {
        this.imageOnlyPages = imageOnlyPages == null ? new ArrayList<>() : imageOnlyPages;
    }

    public boolean isOcrRecommended() {
        return ocrRecommended;
    }

    public void setOcrRecommended(boolean ocrRecommended) {
        this.ocrRecommended = ocrRecommended;
    }

    public boolean isHighQualityProviderRecommended() {
        return highQualityProviderRecommended;
    }

    public void setHighQualityProviderRecommended(boolean highQualityProviderRecommended) {
        this.highQualityProviderRecommended = highQualityProviderRecommended;
    }

    public boolean isFallbackRecommended() {
        return fallbackRecommended;
    }

    public void setFallbackRecommended(boolean fallbackRecommended) {
        this.fallbackRecommended = fallbackRecommended;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }
}
