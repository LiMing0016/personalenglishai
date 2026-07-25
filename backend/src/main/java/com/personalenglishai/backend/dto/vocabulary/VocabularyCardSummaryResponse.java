package com.personalenglishai.backend.dto.vocabulary;

import java.time.LocalDateTime;
import java.util.List;

public record VocabularyCardSummaryResponse(
        String cardUid,
        String displayTerm,
        String normalizedTerm,
        String templateKey,
        String status,
        String activeRevisionUid,
        List<String> sourceTypes,
        LocalDateTime lastCapturedAt,
        LocalDateTime updatedAt,
        String candidateRevisionUid,
        String conflictStatus,
        String generationStatus,
        String generationError,
        String generationOutcome,
        String warning,
        String phonetic,
        String coreDefinition,
        int sourceCount) {

    public VocabularyCardSummaryResponse(
            String cardUid,
            String displayTerm,
            String normalizedTerm,
            String templateKey,
            String status,
            String activeRevisionUid,
            List<String> sourceTypes,
            LocalDateTime lastCapturedAt,
            LocalDateTime updatedAt,
            String candidateRevisionUid,
            String conflictStatus) {
        this(cardUid, displayTerm, normalizedTerm, templateKey, status, activeRevisionUid,
                sourceTypes, lastCapturedAt, updatedAt, candidateRevisionUid, conflictStatus,
                null, null, null, null, null, null, 0);
    }

    public VocabularyCardSummaryResponse(
            String cardUid,
            String displayTerm,
            String normalizedTerm,
            String templateKey,
            String status,
            String activeRevisionUid,
            List<String> sourceTypes,
            LocalDateTime lastCapturedAt,
            LocalDateTime updatedAt) {
        this(cardUid, displayTerm, normalizedTerm, templateKey, status, activeRevisionUid,
                sourceTypes, lastCapturedAt, updatedAt, null, "none",
                null, null, null, null, null, null, 0);
    }
}
