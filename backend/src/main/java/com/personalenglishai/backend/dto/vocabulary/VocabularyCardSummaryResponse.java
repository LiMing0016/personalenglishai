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
        String conflictStatus) {

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
                sourceTypes, lastCapturedAt, updatedAt, null, "none");
    }
}
