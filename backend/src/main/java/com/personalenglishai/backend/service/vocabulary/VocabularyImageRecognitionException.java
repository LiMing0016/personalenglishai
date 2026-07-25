package com.personalenglishai.backend.service.vocabulary;

public final class VocabularyImageRecognitionException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public VocabularyImageRecognitionException(String code, boolean retryable, String message) {
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
