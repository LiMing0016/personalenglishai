package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyTemplateCatalogResponse(
        List<VocabularyTemplateResponse> items,
        String defaultTemplateKey) {
}
