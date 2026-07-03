package com.personalenglishai.backend.service.translation;

public record TranslationOcrOptions(
        DocumentParseMode parseMode,
        Integer pageStart,
        Integer pageEnd,
        Integer maxPages
) {
    public static TranslationOcrOptions of(DocumentParseMode parseMode) {
        return new TranslationOcrOptions(parseMode, null, null, null);
    }

    public static TranslationOcrOptions fromRequest(DocumentParseRequest request) {
        if (request == null) {
            return of(DocumentParseMode.STANDARD);
        }
        return new TranslationOcrOptions(
                request.parseMode(),
                request.pageStart(),
                request.pageEnd(),
                request.maxPages()
        );
    }

    public DocumentParseMode effectiveParseMode() {
        return parseMode == null ? DocumentParseMode.STANDARD : parseMode;
    }

    public int effectiveMaxPages(int fallback) {
        int fallbackValue = Math.max(1, fallback);
        if (maxPages == null) {
            return fallbackValue;
        }
        return Math.max(1, Math.min(500, maxPages));
    }

    public int effectivePageStart() {
        return pageStart == null ? 1 : Math.max(1, pageStart);
    }
}
