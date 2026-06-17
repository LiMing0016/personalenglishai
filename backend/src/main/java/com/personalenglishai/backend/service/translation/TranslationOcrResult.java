package com.personalenglishai.backend.service.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationOcrResult {
    private final String status;
    private final List<TranslationOcrPageText> pages;
    private final String message;
    private final String rawResponse;

    private TranslationOcrResult(String status, List<TranslationOcrPageText> pages, String message, String rawResponse) {
        this.status = status;
        this.pages = pages == null ? List.of() : new ArrayList<>(pages);
        this.message = message;
        this.rawResponse = rawResponse;
    }

    public static TranslationOcrResult succeeded(List<TranslationOcrPageText> pages) {
        return succeeded(pages, null);
    }

    public static TranslationOcrResult succeeded(List<TranslationOcrPageText> pages, String rawResponse) {
        return new TranslationOcrResult("SUCCEEDED", pages, null, rawResponse);
    }

    public static TranslationOcrResult unavailable(String message) {
        return new TranslationOcrResult("UNAVAILABLE", List.of(), message, null);
    }

    public static TranslationOcrResult failed(String message) {
        return new TranslationOcrResult("FAILED", List.of(), message, null);
    }

    public String getStatus() {
        return status;
    }

    public List<TranslationOcrPageText> getPages() {
        return pages;
    }

    public String getMessage() {
        return message;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public boolean isSucceeded() {
        return "SUCCEEDED".equals(status);
    }
}
