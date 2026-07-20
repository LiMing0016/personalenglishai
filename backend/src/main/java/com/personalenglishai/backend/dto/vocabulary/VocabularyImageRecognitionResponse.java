package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyImageRecognitionResponse(
        int contractVersion,
        String traceId,
        String rawText,
        List<String> warnings,
        List<Item> items) {

    public VocabularyImageRecognitionResponse {
        warnings = List.copyOf(warnings);
        items = List.copyOf(items);
    }

    public record Item(
            String itemId,
            String observedText,
            String normalizedTerm,
            String status,
            List<Suggestion> suggestions,
            String contextText,
            double confidence) {

        public Item {
            suggestions = List.copyOf(suggestions);
        }
    }

    public record Suggestion(String term, boolean dictionaryVerified) {
    }
}
