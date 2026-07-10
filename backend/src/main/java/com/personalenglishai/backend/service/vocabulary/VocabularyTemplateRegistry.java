package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyTemplateRegistry {

    private static final int MAX_SCALAR_LENGTH = 2000;
    private static final int MAX_ARRAY_ITEM_LENGTH = 500;
    private static final int MAX_ARRAY_ITEMS = 50;
    private static final Set<String> ARRAY_FIELDS = Set.of(
            "definitions", "examples", "examTips", "collocations", "paraphrases");

    private final Map<String, TemplateDefinition> templates = new LinkedHashMap<>();

    public VocabularyTemplateRegistry(ObjectMapper ignored) {
        templates.put("basic", new TemplateDefinition("basic", 1, "基础单词卡",
                List.of("term", "phonetic", "partOfSpeech", "definitions", "examples", "notes")));
        templates.put("exam", new TemplateDefinition("exam", 1, "考试词汇卡",
                List.of("term", "phonetic", "partOfSpeech", "definitions", "examTips", "collocations", "examples", "notes")));
        templates.put("reading", new TemplateDefinition("reading", 1, "阅读语境卡",
                List.of("term", "definitions", "sourceContext", "contextExplanation", "paraphrases", "notes")));
    }

    public TemplateDefinition require(String key) {
        TemplateDefinition value = templates.get(key == null || key.isBlank() ? "basic" : key);
        if (value == null) {
            throw new IllegalArgumentException("unsupported template: " + key);
        }
        return value;
    }

    public List<VocabularyTemplateResponse> list() {
        return templates.values().stream()
                .map(template -> new VocabularyTemplateResponse(
                        template.key(), template.version(), template.name(), template.requiredFields()))
                .toList();
    }

    public void validate(String key, JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new IllegalArgumentException("content must be an object");
        }
        TemplateDefinition template = require(key);
        Set<String> actualFields = new HashSet<>();
        content.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(Set.copyOf(template.requiredFields()))) {
            throw new IllegalArgumentException("content fields do not match template");
        }
        for (String field : template.requiredFields()) {
            JsonNode value = content.get(field);
            if (value == null || value.isNull()) {
                throw new IllegalArgumentException("missing template field: " + field);
            }
            if (ARRAY_FIELDS.contains(field)) {
                validateArray(field, value);
            } else {
                validateScalar(field, value);
            }
        }
    }

    private void validateScalar(String field, JsonNode value) {
        if (!value.isTextual() || value.textValue().length() > MAX_SCALAR_LENGTH) {
            throw new IllegalArgumentException("invalid template scalar: " + field);
        }
    }

    private void validateArray(String field, JsonNode value) {
        if (!value.isArray() || value.size() > MAX_ARRAY_ITEMS) {
            throw new IllegalArgumentException("invalid template array: " + field);
        }
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().length() > MAX_ARRAY_ITEM_LENGTH) {
                throw new IllegalArgumentException("invalid template array item: " + field);
            }
        }
    }

    public record TemplateDefinition(String key, int version, String name, List<String> requiredFields) {
    }
}
