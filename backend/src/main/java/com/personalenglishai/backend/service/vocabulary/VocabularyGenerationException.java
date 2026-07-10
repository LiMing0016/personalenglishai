package com.personalenglishai.backend.service.vocabulary;

public final class VocabularyGenerationException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public VocabularyGenerationException(String code, boolean retryable, String message) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
