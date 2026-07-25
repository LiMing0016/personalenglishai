package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyThemeCatalogResponse(
        List<VocabularyThemeResponse> systemThemes,
        List<VocabularyThemeResponse> userThemes,
        String defaultThemeUid,
        List<String> recentThemeUids) {
}
