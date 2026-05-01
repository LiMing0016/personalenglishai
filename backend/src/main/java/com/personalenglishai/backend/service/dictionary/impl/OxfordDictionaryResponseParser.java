package com.personalenglishai.backend.service.dictionary.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class OxfordDictionaryResponseParser {

    private final ObjectMapper objectMapper;

    public OxfordDictionaryResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DictionaryLookupResponse parse(String requestedWord, String language, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            DictionaryLookupResponse response = new DictionaryLookupResponse();
            response.setWord(resolveWord(root, requestedWord));
            response.setLanguage(language);

            Map<String, DictionaryPhoneticDto> phonetics = new LinkedHashMap<>();
            Map<String, DictionaryEntryDto> entriesByPartOfSpeech = new LinkedHashMap<>();

            for (JsonNode result : root.path("results")) {
                for (JsonNode lexicalEntry : result.path("lexicalEntries")) {
                    collectPronunciations(lexicalEntry.path("pronunciations"), phonetics);
                    String partOfSpeech = resolvePartOfSpeech(lexicalEntry.path("lexicalCategory"));
                    DictionaryEntryDto entry = entriesByPartOfSpeech.computeIfAbsent(
                            partOfSpeech,
                            DictionaryEntryDto::new
                    );
                    for (JsonNode entryNode : lexicalEntry.path("entries")) {
                        collectPronunciations(entryNode.path("pronunciations"), phonetics);
                        collectSenses(entryNode.path("senses"), entry);
                    }
                }
            }

            response.setPhonetics(phonetics.values().stream().toList());
            response.setEntries(entriesByPartOfSpeech.values().stream()
                    .filter(entry -> !entry.getDefinitions().isEmpty() || !entry.getExamples().isEmpty())
                    .toList());
            return response;
        } catch (Exception e) {
            throw new IllegalArgumentException("Oxford response parse failed", e);
        }
    }

    private String resolveWord(JsonNode root, String fallback) {
        JsonNode results = root.path("results");
        if (results.isArray() && !results.isEmpty()) {
            String word = results.get(0).path("word").asText("");
            if (!word.isBlank()) {
                return word;
            }
        }
        return fallback;
    }

    private String resolvePartOfSpeech(JsonNode lexicalCategory) {
        String text = lexicalCategory.path("text").asText("");
        if (!text.isBlank()) {
            return text;
        }
        String id = lexicalCategory.path("id").asText("");
        return id.isBlank() ? "unknown" : id;
    }

    private void collectPronunciations(JsonNode pronunciations, Map<String, DictionaryPhoneticDto> output) {
        if (!pronunciations.isArray()) {
            return;
        }
        for (JsonNode pronunciation : pronunciations) {
            String text = pronunciation.path("phoneticSpelling").asText("");
            String audioUrl = pronunciation.path("audioFile").asText("");
            if (text.isBlank() && audioUrl.isBlank()) {
                continue;
            }
            String key = text + "|" + audioUrl;
            output.putIfAbsent(key, new DictionaryPhoneticDto(blankToNull(text), blankToNull(audioUrl)));
        }
    }

    private void collectSenses(JsonNode senses, DictionaryEntryDto entry) {
        if (!senses.isArray()) {
            return;
        }
        Set<String> definitions = new LinkedHashSet<>(entry.getDefinitions());
        Set<String> examples = new LinkedHashSet<>(entry.getExamples());
        for (JsonNode sense : senses) {
            collectTextArray(sense.path("definitions"), definitions);
            if (sense.path("definitions").isMissingNode() || sense.path("definitions").isEmpty()) {
                collectTextArray(sense.path("shortDefinitions"), definitions);
            }
            collectExamples(sense.path("examples"), examples);
            collectSenses(sense.path("subsenses"), entry);
        }
        entry.setDefinitions(definitions.stream().toList());
        entry.setExamples(examples.stream().toList());
    }

    private void collectTextArray(JsonNode values, Set<String> output) {
        if (!values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            String text = value.asText("");
            if (!text.isBlank()) {
                output.add(text);
            }
        }
    }

    private void collectExamples(JsonNode examples, Set<String> output) {
        if (!examples.isArray()) {
            return;
        }
        for (JsonNode example : examples) {
            String text = example.path("text").asText("");
            if (!text.isBlank()) {
                output.add(text);
            }
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
