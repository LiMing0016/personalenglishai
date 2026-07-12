package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;

public record GeneratedVocabularyCard(
        JsonNode core,
        String markdown,
        int contentFormatVersion,
        String model,
        String changeSummary,
        boolean partial) {}
