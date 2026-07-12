package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record VocabularyRevisionResponse(
        String revisionUid,
        String baseRevisionUid,
        String authorType,
        String templateKey,
        Integer templateVersion,
        JsonNode content,
        VocabularyThemeSnapshot theme,
        Integer themeVersion,
        JsonNode core,
        String markdown,
        Integer contentFormatVersion,
        String changeSummary,
        boolean active,
        boolean candidate,
        LocalDateTime createdAt) {
}
