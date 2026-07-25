package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyImportAnalysisResponse(
        int contractVersion,
        String traceId,
        String inputFingerprint,
        String rawText,
        List<String> warnings,
        List<Item> items,
        Generation generation) {

    public VocabularyImportAnalysisResponse {
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
            double confidence,
            String evidence) {
        public Item {
            suggestions = List.copyOf(suggestions);
        }
    }

    public record Suggestion(String term, boolean dictionaryVerified) {
    }

    public record Generation(
            String provider,
            String model,
            String promptVersion,
            int modelCallCount,
            String traceId,
            Usage usage) {
    }

    public record Usage(Integer inputTokens, Integer outputTokens) {
    }
}
