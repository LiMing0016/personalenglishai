package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVocabularyCardRequest(
        @NotBlank String baseRevisionUid,
        JsonNode core,
        @Size(max = 20000) String markdown,
        JsonNode content,
        JsonNode cardBlocks,
        @Size(max = 255) String changeSummary) {

    public UpdateVocabularyCardRequest(
            String baseRevisionUid,
            JsonNode core,
            String markdown,
            JsonNode content,
            String changeSummary) {
        this(baseRevisionUid, core, markdown, content, null, changeSummary);
    }
}
