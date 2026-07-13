package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;

public record VocabularyConflictResponse(
        String currentRevisionUid,
        String candidateRevisionUid,
        JsonNode currentContent,
        JsonNode candidateContent,
        Integer currentContentFormatVersion,
        Integer candidateContentFormatVersion,
        String conflictStatus) {
}
