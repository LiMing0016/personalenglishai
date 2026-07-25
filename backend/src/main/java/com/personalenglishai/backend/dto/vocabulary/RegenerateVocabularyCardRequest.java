package com.personalenglishai.backend.dto.vocabulary;

import jakarta.validation.constraints.Pattern;

public record RegenerateVocabularyCardRequest(
        String themeUid,
        Boolean useLatestThemeVersion,
        @Pattern(regexp = "basic|exam|reading") String templateKey) {
}
