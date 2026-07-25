package com.personalenglishai.backend.dto.vocabulary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVocabularyThemeRequest(
        @NotBlank @Size(min = 1, max = 80) String name,
        @NotBlank @Size(min = 1, max = 1000) String purpose) {
}
