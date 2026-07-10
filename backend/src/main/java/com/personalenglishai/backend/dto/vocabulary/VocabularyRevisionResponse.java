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
        String changeSummary,
        boolean active,
        boolean candidate,
        LocalDateTime createdAt) {
}
