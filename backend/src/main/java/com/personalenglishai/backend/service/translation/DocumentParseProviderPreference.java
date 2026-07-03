package com.personalenglishai.backend.service.translation;

import java.util.Locale;

public enum DocumentParseProviderPreference {
    AUTO("auto"),
    PADDLE_OCR("paddle-ocr"),
    LOCAL_PADDLE_VL("local-paddle-vl");

    private final String wireName;

    DocumentParseProviderPreference(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static DocumentParseProviderPreference fromWireName(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DocumentParseProviderPreference candidate : values()) {
            if (candidate.wireName.equals(normalized)) {
                return candidate;
            }
        }
        return AUTO;
    }
}
