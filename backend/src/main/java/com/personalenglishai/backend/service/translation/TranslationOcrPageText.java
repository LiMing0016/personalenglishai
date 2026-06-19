package com.personalenglishai.backend.service.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationOcrPageText {
    private final int pageNumber;
    private final String text;
    private final List<TranslationOcrElement> elements;
    private final List<String> warnings;
    private final Integer width;
    private final Integer height;
    private final Double confidence;
    private final String rawText;
    private final String cleanedText;

    public TranslationOcrPageText(int pageNumber, String text) {
        this(pageNumber, text, List.of(), List.of(), null, null, null, null, null);
    }

    public TranslationOcrPageText(
            int pageNumber,
            String text,
            List<TranslationOcrElement> elements,
            List<String> warnings,
            Integer width,
            Integer height,
            Double confidence,
            String rawText,
            String cleanedText) {
        this.pageNumber = pageNumber;
        this.text = text;
        this.elements = elements == null ? List.of() : new ArrayList<>(elements);
        this.warnings = warnings == null ? List.of() : new ArrayList<>(warnings);
        this.width = width;
        this.height = height;
        this.confidence = confidence;
        this.rawText = rawText;
        this.cleanedText = cleanedText;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getText() {
        return text;
    }

    public List<TranslationOcrElement> getElements() {
        return elements;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getRawText() {
        return rawText;
    }

    public String getCleanedText() {
        return cleanedText;
    }
}
