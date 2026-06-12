package com.personalenglishai.backend.service.translation;

import java.util.Locale;

public enum DocumentParseMode {
    STANDARD("standard"),
    HIGH_QUALITY("high_quality");

    private final String wireName;

    DocumentParseMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static DocumentParseMode fromWireName(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (DocumentParseMode mode : values()) {
            if (mode.wireName.equals(normalized)) {
                return mode;
            }
        }
        return STANDARD;
    }
}
