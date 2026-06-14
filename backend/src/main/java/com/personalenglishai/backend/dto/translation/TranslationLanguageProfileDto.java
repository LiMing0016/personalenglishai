package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationLanguageProfileDto {
    private String primaryLanguage = "unknown";
    private List<String> secondaryLanguages = new ArrayList<>();
    private String languageMixType = "UNKNOWN";
    private double languageConfidence;

    public String getPrimaryLanguage() {
        return primaryLanguage;
    }

    public void setPrimaryLanguage(String primaryLanguage) {
        this.primaryLanguage = primaryLanguage;
    }

    public List<String> getSecondaryLanguages() {
        return secondaryLanguages;
    }

    public void setSecondaryLanguages(List<String> secondaryLanguages) {
        this.secondaryLanguages = secondaryLanguages == null ? new ArrayList<>() : secondaryLanguages;
    }

    public String getLanguageMixType() {
        return languageMixType;
    }

    public void setLanguageMixType(String languageMixType) {
        this.languageMixType = languageMixType;
    }

    public double getLanguageConfidence() {
        return languageConfidence;
    }

    public void setLanguageConfidence(double languageConfidence) {
        this.languageConfidence = languageConfidence;
    }
}
