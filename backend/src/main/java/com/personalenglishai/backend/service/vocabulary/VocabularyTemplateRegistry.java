package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyTemplateRegistry {

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
        for (String field : require(key).requiredFields()) {
            if (!content.has(field) || content.get(field).isNull()) {
                throw new IllegalArgumentException("missing template field: " + field);
            }
        }
        if (!content.get("term").isTextual() || !content.get("definitions").isArray()) {
            throw new IllegalArgumentException("invalid term or definitions field");
        }
    }

    public record TemplateDefinition(String key, int version, String name, List<String> requiredFields) {
    }
}
