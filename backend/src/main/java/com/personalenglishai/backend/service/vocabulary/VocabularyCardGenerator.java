package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardSource;
import com.personalenglishai.backend.service.vocabulary.VocabularyTemplateRegistry.TemplateDefinition;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class VocabularyCardGenerator {

    private static final Logger log = LoggerFactory.getLogger(VocabularyCardGenerator.class);
    private static final String DICTIONARY_LANGUAGE = "en-gb";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final double TEMPERATURE = 0.2;
    private static final int MAX_TOKENS = 1200;

    private final VocabularyDictionaryEnricher dictionaryEnricher;
    private final OpenAiClient openAiClient;
    private final VocabularyGenerationCache cache;
    private final VocabularyTemplateRegistry templateRegistry;
    private final ObjectMapper objectMapper;

    public VocabularyCardGenerator(
            VocabularyDictionaryEnricher dictionaryEnricher,
            OpenAiClient openAiClient,
            VocabularyGenerationCache cache,
            VocabularyTemplateRegistry templateRegistry,
            ObjectMapper objectMapper) {
        this.dictionaryEnricher = dictionaryEnricher;
        this.openAiClient = openAiClient;
        this.cache = cache;
        this.templateRegistry = templateRegistry;
        this.objectMapper = objectMapper;
    }

    public GeneratedVocabularyCard generate(
            VocabularyCard card,
            List<VocabularyCardSource> sources,
            TemplateDefinition template,
            String traceId) {
        requireInputs(card, template);
        DictionaryLookupResponse dictionaryData = lookupDictionary(card, traceId);
        String capturedContext = firstCapturedContext(sources);
        String cacheKey = cache.key(
                card.getNormalizedTerm(), template.key(), template.version(), dictionaryData, capturedContext);

        Optional<ObjectNode> cached = cache.get(cacheKey)
                .flatMap(content -> prepareCachedContent(
                        cacheKey, card, template, content, dictionaryData, capturedContext, traceId));
        if (cached.isPresent()) {
            return new GeneratedVocabularyCard(cached.get(), "cache", "Reused validated generated content");
        }

        String rawOutput = callAi(card, dictionaryData, capturedContext, template, traceId);
        ObjectNode generated = parseObject(rawOutput);
        ObjectNode merged = mergeDictionaryTruth(generated, dictionaryData, template);
        merged.put("term", valueOrEmpty(card.getDisplayTerm()));
        if ("reading".equals(template.key())) {
            merged.put("sourceContext", capturedContext);
        } else {
            merged.remove("sourceContext");
        }
        validateGeneratedContent(template, merged);
        cache.put(cacheKey, merged, CACHE_TTL);
        return new GeneratedVocabularyCard(
                merged, valueOrEmpty(openAiClient.getModel()), "AI generated with " + template.name());
    }

    private DictionaryLookupResponse lookupDictionary(VocabularyCard card, String traceId) {
        try {
            return dictionaryEnricher.lookupWithoutUserState(card.getDisplayTerm(), DICTIONARY_LANGUAGE);
        } catch (RuntimeException exception) {
            log.warn("Vocabulary dictionary enrichment failed traceId={} reasonType={}",
                    safeTraceId(traceId), exception.getClass().getSimpleName());
            throw new VocabularyGenerationException(
                    "DICTIONARY_LOOKUP_FAILED", true, "Dictionary enrichment failed");
        }
    }

    private String callAi(
            VocabularyCard card,
            DictionaryLookupResponse dictionaryData,
            String capturedContext,
            TemplateDefinition template,
            String traceId) {
        try {
            return openAiClient.callWithTraceId(
                    systemPrompt(template),
                    userPrompt(card, dictionaryData, capturedContext),
                    traceId,
                    TEMPERATURE,
                    MAX_TOKENS);
        } catch (RuntimeException exception) {
            log.warn("Vocabulary AI generation failed traceId={} reasonType={}",
                    safeTraceId(traceId), exception.getClass().getSimpleName());
            throw new VocabularyGenerationException("AI_CALL_FAILED", true, "AI generation failed");
        }
    }

    private Optional<ObjectNode> prepareCachedContent(
            String cacheKey,
            VocabularyCard card,
            TemplateDefinition template,
            JsonNode content,
            DictionaryLookupResponse dictionaryData,
            String capturedContext,
            String traceId) {
        try {
            if (!content.isObject()) {
                throw new IllegalArgumentException("cached content is not an object");
            }
            ObjectNode rebuilt = mergeDictionaryTruth((ObjectNode) content.deepCopy(), dictionaryData, template);
            rebuilt.put("term", valueOrEmpty(card.getDisplayTerm()));
            if ("reading".equals(template.key())) {
                rebuilt.put("sourceContext", capturedContext);
            }
            templateRegistry.validate(template.key(), rebuilt);
            return Optional.of(rebuilt);
        } catch (IllegalArgumentException exception) {
            log.warn("Vocabulary generation cache entry rejected traceId={} template={}",
                    safeTraceId(traceId), template.key());
            cache.evict(cacheKey);
            return Optional.empty();
        }
    }

    private ObjectNode parseObject(String rawOutput) {
        try {
            JsonNode parsed = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(stripCodeFences(rawOutput));
            if (parsed == null || !parsed.isObject()) {
                throw invalidOutput();
            }
            return (ObjectNode) parsed;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidOutput();
        }
    }

    private ObjectNode mergeDictionaryTruth(
            ObjectNode aiContent,
            DictionaryLookupResponse dictionaryData,
            TemplateDefinition template) {
        ObjectNode merged = aiContent.deepCopy();
        if (dictionaryData == null) {
            return merged;
        }

        if (template.requiredFields().contains("phonetic")) {
            firstPhonetic(dictionaryData).ifPresent(value -> merged.put("phonetic", value));
        }
        if (template.requiredFields().contains("partOfSpeech")) {
            firstPartOfSpeech(dictionaryData).ifPresent(value -> merged.put("partOfSpeech", value));
        }
        if (template.requiredFields().contains("definitions")) {
            overwriteArrayWhenPresent(merged, "definitions", dictionaryDefinitions(dictionaryData));
        }
        if (template.requiredFields().contains("examples")) {
            overwriteArrayWhenPresent(merged, "examples", dictionaryExamples(dictionaryData));
        }
        return merged;
    }

    private Optional<String> firstPhonetic(DictionaryLookupResponse response) {
        return response.getPhonetics().stream()
                .filter(value -> value != null && value.getText() != null && !value.getText().isBlank())
                .map(DictionaryPhoneticDto::getText)
                .findFirst();
    }

    private Optional<String> firstPartOfSpeech(DictionaryLookupResponse response) {
        return response.getEntries().stream()
                .filter(value -> value != null
                        && value.getPartOfSpeech() != null
                        && !value.getPartOfSpeech().isBlank())
                .map(DictionaryEntryDto::getPartOfSpeech)
                .findFirst();
    }

    private List<String> dictionaryDefinitions(DictionaryLookupResponse response) {
        Set<String> values = new LinkedHashSet<>();
        for (DictionaryEntryDto entry : response.getEntries()) {
            if (entry != null) {
                addNonBlank(values, entry.getDefinitions());
            }
        }
        return List.copyOf(values);
    }

    private List<String> dictionaryExamples(DictionaryLookupResponse response) {
        Set<String> values = new LinkedHashSet<>();
        for (DictionaryEntryDto entry : response.getEntries()) {
            if (entry != null) {
                addNonBlank(values, entry.getExamples());
            }
        }
        return List.copyOf(values);
    }

    private void addNonBlank(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(target::add);
    }

    private void overwriteArrayWhenPresent(ObjectNode target, String field, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        ArrayNode array = target.putArray(field);
        values.forEach(array::add);
    }

    private void validateGeneratedContent(TemplateDefinition template, ObjectNode content) {
        try {
            templateRegistry.validate(template.key(), content);
        } catch (IllegalArgumentException exception) {
            throw invalidOutput();
        }
    }

    private String systemPrompt(TemplateDefinition template) {
        return "Generate a vocabulary card. Return one JSON object without Markdown. "
                + "Required fields in this exact order: "
                + String.join(", ", template.requiredFields())
                + ". Do not add source context that was not provided.";
    }

    private String userPrompt(
            VocabularyCard card,
            DictionaryLookupResponse dictionaryData,
            String capturedContext) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("term", valueOrEmpty(card.getDisplayTerm()));
        input.set("dictionary", dictionaryPromptContent(dictionaryData));
        input.put("capturedSourceContext", capturedContext);
        return input.toString();
    }

    private JsonNode dictionaryPromptContent(DictionaryLookupResponse dictionaryData) {
        if (dictionaryData == null) {
            return objectMapper.createObjectNode();
        }
        ObjectNode content = objectMapper.valueToTree(dictionaryData);
        content.remove(List.of("favorite", "lookupCount"));
        return content;
    }

    private String firstCapturedContext(List<VocabularyCardSource> sources) {
        if (sources == null) {
            return "";
        }
        return sources.stream()
                .filter(source -> source != null
                        && source.getContextText() != null
                        && !source.getContextText().isBlank())
                .map(VocabularyCardSource::getContextText)
                .findFirst()
                .orElse("");
    }

    private String stripCodeFences(String rawOutput) {
        if (rawOutput == null) {
            return "";
        }
        String value = rawOutput.trim();
        if (!value.startsWith("```") || !value.endsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0) {
            return value;
        }
        return value.substring(firstLineEnd + 1, value.length() - 3).trim();
    }

    private void requireInputs(VocabularyCard card, TemplateDefinition template) {
        if (card == null || template == null) {
            throw new VocabularyGenerationException(
                    "INVALID_GENERATION_REQUEST", false, "Vocabulary generation request is incomplete");
        }
    }

    private VocabularyGenerationException invalidOutput() {
        return new VocabularyGenerationException(
                "INVALID_AI_OUTPUT", true, "AI output failed structured validation");
    }

    private String safeTraceId(String traceId) {
        if (traceId == null) {
            return "";
        }
        String sanitized = traceId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
