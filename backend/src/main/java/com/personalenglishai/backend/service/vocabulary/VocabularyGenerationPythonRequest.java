package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonPropertyOrder({
        "contractVersion", "coreSchemaVersion", "requestId", "traceId", "timeoutBudgetMs",
        "term", "dictionaryCore", "sourceContext", "theme"
})
public record VocabularyGenerationPythonRequest(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("timeoutBudgetMs") int timeoutBudgetMs,
        @JsonProperty("term") String term,
        @JsonProperty("dictionaryCore") Core dictionaryCore,
        @JsonProperty("sourceContext") String sourceContext,
        @JsonProperty("theme") Theme theme) {

    static final int VERSION = 1;
    static final int MAX_TIMEOUT_BUDGET_MS = 60_000;
    private static final int MAX_TERM_LENGTH = 200;
    private static final int MAX_SOURCE_CONTEXT_LENGTH = 10_000;
    private static final int MAX_SCALAR_LENGTH = 2_000;
    private static final int MAX_PHONETIC_COUNT = 10;
    private static final int MAX_SENSE_COUNT = 20;
    private static final int MAX_MEANING_COUNT = 30;
    private static final int MAX_OPAQUE_ID_LENGTH = 128;

    public VocabularyGenerationPythonRequest {
        requireOpaqueId(requestId, "requestId");
        requireOpaqueId(traceId, "traceId");
        if (timeoutBudgetMs < 1 || timeoutBudgetMs > MAX_TIMEOUT_BUDGET_MS) {
            throw invalid("timeoutBudgetMs must be between 1 and 60000");
        }
        requireNonBlank(term, "term", MAX_TERM_LENGTH);
        if (dictionaryCore == null || !term.equals(dictionaryCore.term())) {
            throw invalid("dictionaryCore.term must match term");
        }
        if (sourceContext == null || sourceContext.length() > MAX_SOURCE_CONTEXT_LENGTH) {
            throw invalid("sourceContext is invalid");
        }
        if (theme == null) {
            throw invalid("theme is required");
        }
    }

    @JsonProperty("contractVersion")
    public int contractVersion() {
        return VERSION;
    }

    @JsonProperty("coreSchemaVersion")
    public int coreSchemaVersion() {
        return VERSION;
    }

    @JsonPropertyOrder({"schemaVersion", "term", "phonetics", "senses"})
    public record Core(
            @JsonProperty("term") String term,
            @JsonProperty("phonetics") List<Phonetic> phonetics,
            @JsonProperty("senses") List<Sense> senses) {

        public Core {
            requireNonBlank(term, "core.term", MAX_TERM_LENGTH);
            phonetics = immutableList(phonetics, MAX_PHONETIC_COUNT, "phonetics");
            senses = immutableList(senses, MAX_SENSE_COUNT, "senses");
        }

        @JsonProperty("schemaVersion")
        public int schemaVersion() {
            return VERSION;
        }

        static Core fromJson(JsonNode node) {
            requireExactFields(node, Set.of("schemaVersion", "term", "phonetics", "senses"), "core");
            requireVersion(required(node, "schemaVersion"), "core.schemaVersion");
            List<Phonetic> phonetics = new ArrayList<>();
            for (JsonNode phonetic : array(required(node, "phonetics"), "core.phonetics")) {
                requireExactFields(phonetic, Set.of("region", "text", "audioUrl"), "phonetic");
                JsonNode audioUrl = required(phonetic, "audioUrl");
                phonetics.add(new Phonetic(
                        text(required(phonetic, "region"), "phonetic.region", MAX_SCALAR_LENGTH),
                        text(required(phonetic, "text"), "phonetic.text", MAX_SCALAR_LENGTH),
                        audioUrl.isNull() ? null : text(audioUrl, "phonetic.audioUrl", MAX_SCALAR_LENGTH)));
            }
            List<Sense> senses = new ArrayList<>();
            for (JsonNode sense : array(required(node, "senses"), "core.senses")) {
                requireExactFields(sense, Set.of("partOfSpeech", "meanings"), "sense");
                List<Meaning> meanings = new ArrayList<>();
                for (JsonNode meaning : array(required(sense, "meanings"), "sense.meanings")) {
                    requireExactFields(meaning, Set.of("definitionEn", "definitionZh"), "meaning");
                    meanings.add(new Meaning(
                            text(required(meaning, "definitionEn"), "meaning.definitionEn", MAX_SCALAR_LENGTH),
                            text(required(meaning, "definitionZh"), "meaning.definitionZh", MAX_SCALAR_LENGTH)));
                }
                senses.add(new Sense(
                        text(required(sense, "partOfSpeech"), "sense.partOfSpeech", MAX_SCALAR_LENGTH), meanings));
            }
            return new Core(text(required(node, "term"), "core.term", MAX_TERM_LENGTH), phonetics, senses);
        }
    }

    public record Phonetic(
            @JsonProperty("region") String region,
            @JsonProperty("text") String text,
            @JsonProperty("audioUrl") String audioUrl) {

        public Phonetic {
            if (!Set.of("uk", "us", "other").contains(region)) {
                throw invalid("phonetic.region is invalid");
            }
            requireText(text, "phonetic.text", MAX_SCALAR_LENGTH);
            if (audioUrl != null) {
                requireText(audioUrl, "phonetic.audioUrl", MAX_SCALAR_LENGTH);
            }
        }
    }

    public record Sense(
            @JsonProperty("partOfSpeech") String partOfSpeech,
            @JsonProperty("meanings") List<Meaning> meanings) {

        public Sense {
            requireText(partOfSpeech, "sense.partOfSpeech", MAX_SCALAR_LENGTH);
            meanings = immutableList(meanings, MAX_MEANING_COUNT, "meanings");
        }
    }

    public record Meaning(
            @JsonProperty("definitionEn") String definitionEn,
            @JsonProperty("definitionZh") String definitionZh) {

        public Meaning {
            requireText(definitionEn, "meaning.definitionEn", MAX_SCALAR_LENGTH);
            requireText(definitionZh, "meaning.definitionZh", MAX_SCALAR_LENGTH);
        }
    }

    public record Theme(
            @JsonProperty("uid") String uid,
            @JsonProperty("version") int version,
            @JsonProperty("name") String name,
            @JsonProperty("purpose") String purpose,
            @JsonProperty("promptStrategyKey") String promptStrategyKey,
            @JsonProperty("contentFormatVersion") int contentFormatVersion) {

        public Theme {
            requireNonBlank(uid, "theme.uid", 200);
            if (version < 1) {
                throw invalid("theme.version is invalid");
            }
            requireNonBlank(name, "theme.name", 200);
            if (purpose == null || purpose.length() > MAX_SOURCE_CONTEXT_LENGTH) {
                throw invalid("theme.purpose is invalid");
            }
            if (!Set.of("basic-markdown-v1", "exam-markdown-v1", "reading-markdown-v1", "custom-markdown-v1")
                    .contains(promptStrategyKey)) {
                throw invalid("theme.promptStrategyKey is invalid");
            }
            if (contentFormatVersion != VERSION) {
                throw invalid("theme.contentFormatVersion must be 1");
            }
        }
    }

    private static <T> List<T> immutableList(List<T> value, int maxSize, String field) {
        if (value == null || value.size() > maxSize || value.stream().anyMatch(item -> item == null)) {
            throw invalid(field + " is invalid");
        }
        return List.copyOf(value);
    }

    private static void requireOpaqueId(String value, String field) {
        requireNonBlank(value, field, MAX_OPAQUE_ID_LENGTH);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw invalid(field + " is invalid");
        }
    }

    private static void requireNonBlank(String value, String field, int maxLength) {
        requireText(value, field, maxLength);
        if (value.isBlank()) {
            throw invalid(field + " is blank");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.length() > maxLength) {
            throw invalid(field + " is invalid");
        }
    }

    private static void requireExactFields(JsonNode node, Set<String> expected, String field) {
        if (node == null || !node.isObject()) {
            throw invalid(field + " must be an object");
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid(field + " has an invalid field set");
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw invalid("missing field: " + field);
        }
        return value;
    }

    private static List<JsonNode> array(JsonNode node, String field) {
        if (!node.isArray()) {
            throw invalid(field + " must be an array");
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private static String text(JsonNode node, String field, int maxLength) {
        if (!node.isTextual()) {
            throw invalid(field + " must be text");
        }
        String value = node.textValue();
        requireText(value, field, maxLength);
        return value;
    }

    private static void requireVersion(JsonNode node, String field) {
        if (!node.isInt() || node.intValue() != VERSION) {
            throw invalid(field + " must be 1");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
