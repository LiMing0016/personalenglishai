package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVocabularyCardRequest(
        @NotBlank String baseRevisionUid,
        JsonNode core,
        @Size(max = 20000) String markdown,
        JsonNode content,
        @Size(max = 255) String changeSummary) {
}
