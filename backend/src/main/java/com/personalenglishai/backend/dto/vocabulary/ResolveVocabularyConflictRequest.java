package com.personalenglishai.backend.dto.vocabulary;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record ResolveVocabularyConflictRequest(
        @NotBlank
        @Pattern(regexp = "keep_current|use_ai|merge_fields") String choice,
        @JsonAlias("merge_fields") Map<String, JsonNode> mergeFields) {
}
