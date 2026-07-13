package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;

public record GeneratedVocabularyCard(
        JsonNode core,
        String markdown,
        int contentFormatVersion,
        String model,
        String changeSummary,
        boolean partial,
        String generationOutcome,
        String warning) {

    public GeneratedVocabularyCard(
            JsonNode core,
            String markdown,
            int contentFormatVersion,
            String model,
            String changeSummary,
            boolean partial) {
        this(core, markdown, contentFormatVersion, model, changeSummary, partial,
                partial ? "partial" : "complete",
                partial ? "markdown_unavailable" : null);
    }
}
