package com.personalenglishai.backend.dto.vocabulary;

import java.util.List;

public record VocabularyTemplateResponse(String key, int version, String name, List<String> fields) {
}
