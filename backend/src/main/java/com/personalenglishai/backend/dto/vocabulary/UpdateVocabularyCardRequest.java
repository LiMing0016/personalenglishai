package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVocabularyCardRequest(
        @NotBlank String baseRevisionUid,
        @NotNull JsonNode content,
        @Size(max = 255) String changeSummary) {
}
