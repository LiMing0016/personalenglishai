package com.personalenglishai.backend.service.vocabulary;

public record ResolvedVocabularyTheme(
        String themeUid,
        int version,
        String name,
        String purpose,
        String promptStrategyKey,
        int contentFormatVersion,
        String legacyTemplateKey) {
}
