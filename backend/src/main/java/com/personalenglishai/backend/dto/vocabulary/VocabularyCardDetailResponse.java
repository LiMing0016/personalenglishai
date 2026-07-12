package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public record VocabularyCardDetailResponse(
        String cardUid,
        String displayTerm,
        String normalizedTerm,
        String language,
        String templateKey,
        Integer templateVersion,
        String status,
        String activeRevisionUid,
        List<String> sourceTypes,
        JsonNode content,
        List<SourceItem> sources,
        String generationStatus,
        String generationError,
        LocalDateTime lastCapturedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String candidateRevisionUid,
        JsonNode candidateContent,
        String conflictStatus,
        VocabularyThemeSnapshot theme,
        Integer themeVersion,
        JsonNode core,
        String markdown,
        Integer contentFormatVersion) {

    public VocabularyCardDetailResponse(
            String cardUid,
            String displayTerm,
            String normalizedTerm,
            String language,
            String templateKey,
            Integer templateVersion,
            String status,
            String activeRevisionUid,
            List<String> sourceTypes,
            JsonNode content,
            List<SourceItem> sources,
            String generationStatus,
            String generationError,
            LocalDateTime lastCapturedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(cardUid, displayTerm, normalizedTerm, language, templateKey, templateVersion, status,
                activeRevisionUid, sourceTypes, content, sources, generationStatus, generationError,
                lastCapturedAt, createdAt, updatedAt, null, null, "none",
                null, null, null, null, null);
    }

    public record SourceItem(
            String sourceUid,
            String sourceType,
            String sourceRef,
            String sourceTitle,
            String sourceUrl,
            String contextText,
            String rawTerm,
            JsonNode metadata,
            LocalDateTime capturedAt,
            LocalDateTime createdAt) {
    }
}
