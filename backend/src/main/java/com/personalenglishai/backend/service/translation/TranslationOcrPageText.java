package com.personalenglishai.backend.service.translation;

public class TranslationOcrPageText {
    private final int pageNumber;
    private final String text;

    public TranslationOcrPageText(int pageNumber, String text) {
        this.pageNumber = pageNumber;
        this.text = text;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getText() {
        return text;
    }
}
