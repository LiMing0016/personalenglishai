package com.personalenglishai.backend.dto.vocabulary;

public record VocabularyCardResolutionResponse(boolean found, String cardUid) {
    public static VocabularyCardResolutionResponse found(String cardUid) {
        return new VocabularyCardResolutionResponse(true, cardUid);
    }

    public static VocabularyCardResolutionResponse notFound() {
        return new VocabularyCardResolutionResponse(false, null);
    }
}
