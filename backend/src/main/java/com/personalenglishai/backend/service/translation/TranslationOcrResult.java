package com.personalenglishai.backend.service.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationOcrResult {
    private final String status;
    private final List<TranslationOcrPageText> pages;
    private final String message;

    private TranslationOcrResult(String status, List<TranslationOcrPageText> pages, String message) {
        this.status = status;
        this.pages = pages == null ? List.of() : new ArrayList<>(pages);
        this.message = message;
    }

    public static TranslationOcrResult succeeded(List<TranslationOcrPageText> pages) {
        return new TranslationOcrResult("SUCCEEDED", pages, null);
    }

    public static TranslationOcrResult unavailable(String message) {
        return new TranslationOcrResult("UNAVAILABLE", List.of(), message);
    }

    public static TranslationOcrResult failed(String message) {
        return new TranslationOcrResult("FAILED", List.of(), message);
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

    public boolean isSucceeded() {
        return "SUCCEEDED".equals(status);
    }
}
