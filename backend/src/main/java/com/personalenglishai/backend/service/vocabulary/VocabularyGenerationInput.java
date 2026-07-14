package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record VocabularyGenerationInput(
        String term,
        ObjectNode dictionaryCore,
        String sourceContext,
        ResolvedVocabularyTheme theme,
        String traceId) {

    public VocabularyGenerationInput {
        if (term == null || term.isBlank() || dictionaryCore == null || theme == null) {
            throw new IllegalArgumentException("Vocabulary generation input is incomplete");
        }
        dictionaryCore = dictionaryCore.deepCopy();
        sourceContext = sourceContext == null ? "" : sourceContext;
        theme = new ResolvedVocabularyTheme(
                theme.themeUid(), theme.version(), theme.name(), theme.purpose(), theme.promptStrategyKey(),
                theme.contentFormatVersion(), theme.legacyTemplateKey());
        traceId = safeTraceId(traceId);
    }

    @Override
    public ObjectNode dictionaryCore() {
        return dictionaryCore.deepCopy();
    }

    private static String safeTraceId(String traceId) {
        if (traceId == null) {
            return "";
        }
        String sanitized = traceId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
    }
}
