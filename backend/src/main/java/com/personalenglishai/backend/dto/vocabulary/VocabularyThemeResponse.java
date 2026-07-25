package com.personalenglishai.backend.dto.vocabulary;

public record VocabularyThemeResponse(
        String themeUid,
        String ownerType,
        String name,
        String purpose,
        int version,
        String status,
        boolean system,
        boolean defaultTheme,
        boolean recent,
        String promptStrategyKey) {
}
