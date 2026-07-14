package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyCoreContentCodec {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_SCALAR_LENGTH = 2000;
    private static final int MAX_PHONETICS = 10;
    private static final int MAX_SENSES = 20;
    private static final int MAX_MEANINGS = 30;
    private static final Set<String> CORE_FIELDS = Set.of(
            "schemaVersion", "term", "phonetics", "senses");
    private static final Set<String> PHONETIC_FIELDS = Set.of("region", "text", "audioUrl");
    private static final Set<String> SENSE_FIELDS = Set.of("partOfSpeech", "meanings");
    private static final Set<String> MEANING_FIELDS = Set.of("definitionEn", "definitionZh");

    private final ObjectMapper objectMapper;

    public VocabularyCoreContentCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode fromDictionary(String term, DictionaryLookupResponse dictionary) {
        ObjectNode core = emptyCore(term);
        ArrayNode phonetics = (ArrayNode) core.get("phonetics");
        List<DictionaryPhoneticDto> sourcePhonetics = dictionary == null || dictionary.getPhonetics() == null
                ? List.of()
                : dictionary.getPhonetics();
        boolean hasExplicitRegion = sourcePhonetics.stream()
                .filter(this::hasText)
                .anyMatch(phonetic -> explicitRegion(phonetic.getText()) != null);
        for (DictionaryPhoneticDto phonetic : sourcePhonetics) {
            if (phonetic == null || (!hasText(phonetic.getText()) && !hasText(phonetic.getAudioUrl()))) {
                continue;
            }
            if (phonetics.size() >= MAX_PHONETICS) {
                break;
            }
            ObjectNode item = phonetics.addObject();
            item.put("region", phoneticRegion(phonetic, dictionary, hasExplicitRegion));
            item.put("text", valueOrEmpty(phonetic.getText()));
            if (phonetic.getAudioUrl() == null) {
                item.putNull("audioUrl");
            } else {
                item.put("audioUrl", phonetic.getAudioUrl());
            }
        }

        Map<String, ObjectNode> sensesByPartOfSpeech = new LinkedHashMap<>();
        List<DictionaryEntryDto> entries = dictionary == null || dictionary.getEntries() == null
                ? List.of()
                : dictionary.getEntries();
        for (DictionaryEntryDto entry : entries) {
            if (entry == null) {
                continue;
            }
            String partOfSpeech = normalizePartOfSpeech(entry.getPartOfSpeech());
            ObjectNode sense = sensesByPartOfSpeech.get(partOfSpeech);
            if (sense == null) {
                if (sensesByPartOfSpeech.size() >= MAX_SENSES) {
                    continue;
                }
                sense = newSense(partOfSpeech);
                sensesByPartOfSpeech.put(partOfSpeech, sense);
            }
            ArrayNode meanings = (ArrayNode) sense.get("meanings");
            if (entry.getDefinitions() == null) {
                continue;
            }
            for (String definition : entry.getDefinitions()) {
                if (meanings.size() >= MAX_MEANINGS) {
                    break;
                }
                if (hasText(definition)) {
                    meanings.add(splitMeaning(definition));
                }
            }
        }
        ArrayNode senses = (ArrayNode) core.get("senses");
        sensesByPartOfSpeech.values().forEach(senses::add);
        validate(term, core);
        return core;
    }

    public ObjectNode fromLegacy(String term, JsonNode legacy) {
        ObjectNode core = emptyCore(term);
        if (legacy == null || !legacy.isObject()) {
            validate(term, core);
            return core;
        }

        JsonNode phonetic = legacy.get("phonetic");
        if (phonetic != null && phonetic.isTextual() && !phonetic.textValue().isBlank()) {
            ObjectNode item = ((ArrayNode) core.get("phonetics")).addObject();
            item.put("region", "other");
            item.put("text", phonetic.textValue());
            item.putNull("audioUrl");
        }

        JsonNode partOfSpeech = legacy.get("partOfSpeech");
        JsonNode definitions = legacy.get("definitions");
        boolean hasPartOfSpeech = partOfSpeech != null
                && partOfSpeech.isTextual()
                && !partOfSpeech.textValue().isBlank();
        if (hasPartOfSpeech || (definitions != null && definitions.isArray())) {
            String partOfSpeechValue = partOfSpeech == null ? null : partOfSpeech.textValue();
            ObjectNode sense = newSense(normalizePartOfSpeech(partOfSpeechValue));
            ArrayNode meanings = (ArrayNode) sense.get("meanings");
            if (definitions != null && definitions.isArray()) {
                for (JsonNode definition : definitions) {
                    if (meanings.size() >= MAX_MEANINGS) {
                        break;
                    }
                    if (definition.isTextual() && !definition.textValue().isBlank()) {
                        meanings.add(splitMeaning(definition.textValue()));
                    }
                }
            }
            ArrayNode senses = (ArrayNode) core.get("senses");
            if (senses.size() < MAX_SENSES) {
                senses.add(sense);
            }
        }
        validate(term, core);
        return core;
    }

    public void validate(JsonNode core) {
        validate(null, core);
    }

    public void validate(String expectedTerm, JsonNode core) {
        if (core == null || !core.isObject()) {
            throw invalid("core must be an object");
        }
        rejectUnknownFields(core, CORE_FIELDS, "core");
        JsonNode schemaVersion = required(core, "schemaVersion");
        if (!schemaVersion.isInt() || schemaVersion.intValue() != SCHEMA_VERSION) {
            throw invalid("schemaVersion must be 1");
        }
        String actualTerm = textField(required(core, "term"), "term");
        if (expectedTerm != null && !expectedTerm.equals(actualTerm)) {
            throw invalid("term does not match expected card identity");
        }
        validatePhonetics(required(core, "phonetics"));
        validateSenses(required(core, "senses"));
    }

    public void validate(JsonNode core, String expectedTerm) {
        validate(expectedTerm, core);
    }

    public boolean isComplete(String expectedTerm, JsonNode core) {
        try {
            validate(expectedTerm, core);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (firstPhonetic(core) == null) {
            return false;
        }
        for (JsonNode sense : core.path("senses")) {
            if (!hasText(sense.path("partOfSpeech").asText(null))
                    || !sense.path("meanings").isArray()) {
                continue;
            }
            for (JsonNode meaning : sense.path("meanings")) {
                if (textOrNull(meaning.path("definitionEn")) != null
                        || textOrNull(meaning.path("definitionZh")) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public void validatePreservesTrustedFields(
            String expectedTerm, JsonNode trustedCore, JsonNode candidateCore) {
        validate(expectedTerm, trustedCore);
        validate(expectedTerm, candidateCore);

        JsonNode trustedPhonetics = trustedCore.path("phonetics");
        JsonNode candidatePhonetics = candidateCore.path("phonetics");
        requireAtLeastTrustedSize(trustedPhonetics, candidatePhonetics, "phonetics");
        for (int index = 0; index < trustedPhonetics.size(); index++) {
            JsonNode trusted = trustedPhonetics.get(index);
            JsonNode candidate = candidatePhonetics.get(index);
            requireEqual(trusted.path("region"), candidate.path("region"), "phonetic region");
            requireNonblankPreserved(trusted.path("text"), candidate.path("text"), "phonetic text");
            requireNonblankPreserved(
                    trusted.path("audioUrl"), candidate.path("audioUrl"), "phonetic audioUrl");
        }

        JsonNode trustedSenses = trustedCore.path("senses");
        JsonNode candidateSenses = candidateCore.path("senses");
        requireAtLeastTrustedSize(trustedSenses, candidateSenses, "senses");
        for (int senseIndex = 0; senseIndex < trustedSenses.size(); senseIndex++) {
            JsonNode trustedSense = trustedSenses.get(senseIndex);
            JsonNode candidateSense = candidateSenses.get(senseIndex);
            requireNonblankPreserved(
                    trustedSense.path("partOfSpeech"),
                    candidateSense.path("partOfSpeech"),
                    "partOfSpeech");

            JsonNode trustedMeanings = trustedSense.path("meanings");
            JsonNode candidateMeanings = candidateSense.path("meanings");
            requireAtLeastTrustedSize(trustedMeanings, candidateMeanings, "meanings");
            for (int meaningIndex = 0; meaningIndex < trustedMeanings.size(); meaningIndex++) {
                JsonNode trustedMeaning = trustedMeanings.get(meaningIndex);
                JsonNode candidateMeaning = candidateMeanings.get(meaningIndex);
                requireNonblankPreserved(
                        trustedMeaning.path("definitionEn"),
                        candidateMeaning.path("definitionEn"),
                        "definitionEn");
                requireNonblankPreserved(
                        trustedMeaning.path("definitionZh"),
                        candidateMeaning.path("definitionZh"),
                        "definitionZh");
            }
        }
    }

    public String summaryPhonetic(JsonNode core) {
        return firstPhonetic(core);
    }

    public String summaryDefinition(JsonNode core) {
        return firstDefinition(core);
    }

    public String firstPhonetic(JsonNode core) {
        if (core == null || !core.path("phonetics").isArray()) {
            return null;
        }
        for (JsonNode phonetic : core.path("phonetics")) {
            String text = textOrNull(phonetic.path("text"));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    public String firstDefinition(JsonNode core) {
        if (core == null || !core.path("senses").isArray()) {
            return null;
        }
        for (JsonNode sense : core.path("senses")) {
            if (!sense.path("meanings").isArray()) {
                continue;
            }
            for (JsonNode meaning : sense.path("meanings")) {
                String english = textOrNull(meaning.path("definitionEn"));
                if (english != null) {
                    return english;
                }
                String chinese = textOrNull(meaning.path("definitionZh"));
                if (chinese != null) {
                    return chinese;
                }
            }
        }
        return null;
    }

    private ObjectNode emptyCore(String term) {
        ObjectNode core = objectMapper.createObjectNode();
        core.put("schemaVersion", SCHEMA_VERSION);
        core.put("term", valueOrEmpty(term));
        core.putArray("phonetics");
        core.putArray("senses");
        return core;
    }

    private ObjectNode newSense(String partOfSpeech) {
        ObjectNode sense = objectMapper.createObjectNode();
        sense.put("partOfSpeech", partOfSpeech);
        sense.putArray("meanings");
        return sense;
    }

    private ObjectNode splitMeaning(String definition) {
        String value = definition.trim();
        int delimiter = firstBilingualDelimiter(value);
        ObjectNode meaning = objectMapper.createObjectNode();
        if (delimiter < 0) {
            meaning.put("definitionEn", value);
            meaning.put("definitionZh", "");
        } else {
            meaning.put("definitionEn", value.substring(0, delimiter).trim());
            meaning.put("definitionZh", value.substring(delimiter + 1).trim());
        }
        return meaning;
    }

    private int firstBilingualDelimiter(String value) {
        int fullWidth = value.indexOf('；');
        int ascii = value.indexOf(';');
        if (fullWidth < 0) {
            return ascii;
        }
        if (ascii < 0) {
            return fullWidth;
        }
        return Math.min(fullWidth, ascii);
    }

    private String phoneticRegion(
            DictionaryPhoneticDto phonetic,
            DictionaryLookupResponse dictionary,
            boolean hasExplicitRegion) {
        String explicit = explicitRegion(phonetic.getText());
        if (explicit != null) {
            return explicit;
        }
        if (hasExplicitRegion) {
            return "other";
        }
        String language = dictionary == null ? "" : valueOrEmpty(dictionary.getLanguage()).toLowerCase(Locale.ROOT);
        if (language.contains("-gb") || language.contains("-uk")) {
            return "uk";
        }
        if (language.contains("-us")) {
            return "us";
        }
        return "other";
    }

    private String explicitRegion(String text) {
        if (!hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(^|[^a-z])(uk|gb|bre|british)([^a-z]|$).*")) {
            return "uk";
        }
        if (lower.matches(".*(^|[^a-z])(us|ame|american)([^a-z]|$).*")) {
            return "us";
        }
        return null;
    }

    private void validatePhonetics(JsonNode value) {
        if (!value.isArray() || value.size() > MAX_PHONETICS) {
            throw invalid("invalid phonetics");
        }
        for (JsonNode phonetic : value) {
            if (!phonetic.isObject()) {
                throw invalid("phonetic must be an object");
            }
            rejectUnknownFields(phonetic, PHONETIC_FIELDS, "phonetic");
            String region = textField(required(phonetic, "region"), "region");
            if (!Set.of("uk", "us", "other").contains(region)) {
                throw invalid("invalid phonetic region");
            }
            textField(required(phonetic, "text"), "text");
            JsonNode audioUrl = required(phonetic, "audioUrl");
            if (!audioUrl.isNull()) {
                textField(audioUrl, "audioUrl");
            }
        }
    }

    private void validateSenses(JsonNode value) {
        if (!value.isArray() || value.size() > MAX_SENSES) {
            throw invalid("invalid senses");
        }
        for (JsonNode sense : value) {
            if (!sense.isObject()) {
                throw invalid("sense must be an object");
            }
            rejectUnknownFields(sense, SENSE_FIELDS, "sense");
            textField(required(sense, "partOfSpeech"), "partOfSpeech");
            JsonNode meanings = required(sense, "meanings");
            if (!meanings.isArray() || meanings.size() > MAX_MEANINGS) {
                throw invalid("invalid meanings");
            }
            for (JsonNode meaning : meanings) {
                if (!meaning.isObject()) {
                    throw invalid("meaning must be an object");
                }
                rejectUnknownFields(meaning, MEANING_FIELDS, "meaning");
                textField(required(meaning, "definitionEn"), "definitionEn");
                textField(required(meaning, "definitionZh"), "definitionZh");
            }
        }
    }

    private JsonNode required(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null) {
            throw invalid("missing field: " + field);
        }
        return value;
    }

    private String textField(JsonNode value, String field) {
        if (value == null || !value.isTextual() || value.textValue().length() > MAX_SCALAR_LENGTH) {
            throw invalid("invalid text field: " + field);
        }
        return value.textValue();
    }

    private void rejectUnknownFields(JsonNode value, Set<String> allowed, String objectName) {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!allowed.containsAll(actual)) {
            throw invalid("unknown field in " + objectName);
        }
    }

    private void requireAtLeastTrustedSize(JsonNode trusted, JsonNode candidate, String field) {
        if (!trusted.isArray() || !candidate.isArray() || candidate.size() < trusted.size()) {
            throw invalid("candidate removed trusted " + field);
        }
    }

    private void requireNonblankPreserved(JsonNode trusted, JsonNode candidate, String field) {
        if (textOrNull(trusted) != null) {
            requireEqual(trusted, candidate, field);
        }
    }

    private void requireEqual(JsonNode trusted, JsonNode candidate, String field) {
        if (!trusted.equals(candidate)) {
            throw invalid("candidate changed trusted " + field);
        }
    }

    private String normalizePartOfSpeech(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "unknown";
    }

    private String textOrNull(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasText(DictionaryPhoneticDto phonetic) {
        return phonetic != null && (hasText(phonetic.getText()) || hasText(phonetic.getAudioUrl()));
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
