package com.personalenglishai.backend.service.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationOcrElement {
    private final String type;
    private final String text;
    private final String bbox;
    private final Double confidence;
    private final int order;
    private final String source;
    private final String rawType;
    private final List<String> warnings;

    public TranslationOcrElement(
            String type,
            String text,
            String bbox,
            Double confidence,
            int order,
            String source,
            String rawType,
            List<String> warnings) {
        this.type = type;
        this.text = text;
        this.bbox = bbox;
        this.confidence = confidence;
        this.order = order;
        this.source = source;
        this.rawType = rawType;
        this.warnings = warnings == null ? List.of() : new ArrayList<>(warnings);
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getBbox() {
        return bbox;
    }

    public Double getConfidence() {
        return confidence;
    }

    public int getOrder() {
        return order;
    }

    public String getSource() {
        return source;
    }

    public String getRawType() {
        return rawType;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
