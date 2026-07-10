package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;

public record GeneratedVocabularyCard(JsonNode content, String model, String changeSummary) {
}
