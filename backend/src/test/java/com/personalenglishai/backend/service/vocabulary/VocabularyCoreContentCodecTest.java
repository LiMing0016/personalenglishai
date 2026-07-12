package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VocabularyCoreContentCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VocabularyCoreContentCodec codec = new VocabularyCoreContentCodec(objectMapper);

    @Test
    void dictionaryTruthKeepsMultiplePhoneticsAndGroupsMeaningsByPartOfSpeech() {
        DictionaryLookupResponse dictionary = VocabularyTestFixtures.dictionaryLookupWithCoreTruth();

        ObjectNode core = codec.fromDictionary("record", dictionary);

        assertEquals(1, core.path("schemaVersion").asInt());
        assertEquals("record", core.path("term").asText());
        assertEquals(3, core.path("phonetics").size());
        assertEquals("uk", core.path("phonetics").get(0).path("region").asText());
        assertEquals("us", core.path("phonetics").get(1).path("region").asText());
        assertEquals("other", core.path("phonetics").get(2).path("region").asText());
        assertEquals(2, core.path("senses").size());
        assertEquals("noun", core.path("senses").get(0).path("partOfSpeech").asText());
        assertEquals(3, core.path("senses").get(0).path("meanings").size());
        assertEquals("a written account", core.path("senses").get(0).path("meanings").get(0).path("definitionEn").asText());
        assertEquals("记录", core.path("senses").get(0).path("meanings").get(0).path("definitionZh").asText());
        assertEquals("verb", core.path("senses").get(1).path("partOfSpeech").asText());
        codec.validate("record", core);
    }

    @Test
    void dictionaryProjectionAlwaysUsesExpectedTermAndPreservesEmptyArrays() {
        DictionaryLookupResponse dictionary = VocabularyTestFixtures.dictionaryLookup(
                "wrong-word", "noun", "");
        dictionary.setPhonetics(List.of());
        dictionary.setEntries(List.of());

        ObjectNode core = codec.fromDictionary("display-term", dictionary);

        assertEquals("display-term", core.path("term").asText());
        assertTrue(core.path("phonetics").isArray());
        assertTrue(core.path("phonetics").isEmpty());
        assertTrue(core.path("senses").isArray());
        assertTrue(core.path("senses").isEmpty());
    }

    @Test
    void legacyProjectionMapsScalarFieldsWithoutMutatingLegacyContent() {
        ObjectNode legacy = VocabularyTestFixtures.legacyVocabularyContent(objectMapper);
        JsonNode snapshot = legacy.deepCopy();

        ObjectNode core = codec.fromLegacy("record", legacy);

        assertEquals(1, core.path("schemaVersion").asInt());
        assertEquals("record", core.path("term").asText());
        assertEquals("/ˈrekɔːd/", core.path("phonetics").get(0).path("text").asText());
        assertEquals("noun", core.path("senses").get(0).path("partOfSpeech").asText());
        assertEquals("记录", core.path("senses").get(0).path("meanings").get(0).path("definitionZh").asText());
        assertEquals(snapshot, legacy);
        assertFalse(core.has("definitions"));
    }

    @Test
    void validationRejectsUnknownFieldsNonTextValuesLimitsAndUnexpectedTerm() {
        ObjectNode unknownField = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        unknownField.put("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> codec.validate(unknownField));

        ObjectNode nonText = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        ((ObjectNode) nonText.path("senses").get(0).path("meanings").get(0)).set("definitionEn", objectMapper.createObjectNode());
        assertThrows(IllegalArgumentException.class, () -> codec.validate(nonText));

        ObjectNode tooManyPhonetics = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        ArrayNode phonetics = (ArrayNode) tooManyPhonetics.path("phonetics");
        for (int i = 0; i < 10; i++) {
            phonetics.add(objectMapper.createObjectNode()
                    .put("region", "other")
                    .put("text", "x")
                    .putNull("audioUrl"));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.validate(tooManyPhonetics));

        ObjectNode wrongTerm = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        assertThrows(IllegalArgumentException.class, () -> codec.validate("different", wrongTerm));
    }

    @Test
    void summaryHelpersReturnFirstNonBlankPhoneticAndBilingualMeaning() {
        ObjectNode core = codec.fromDictionary("record", VocabularyTestFixtures.dictionaryLookupWithCoreTruth());

        assertEquals("UK /ˈrekɔːd/", codec.summaryPhonetic(core));
        assertEquals("a written account", codec.summaryDefinition(core));
        assertEquals("UK /ˈrekɔːd/", codec.firstPhonetic(core));
        assertEquals("a written account", codec.firstDefinition(core));
    }

    @Test
    void validationRejectsSenseMeaningAndScalarLimits() {
        ObjectNode tooManySenses = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        ArrayNode senses = (ArrayNode) tooManySenses.path("senses");
        for (int i = 0; i < 20; i++) {
            senses.add(objectMapper.createObjectNode()
                    .put("partOfSpeech", "other")
                    .putArray("meanings"));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.validate(tooManySenses));

        ObjectNode tooManyMeanings = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        ArrayNode meanings = (ArrayNode) tooManyMeanings.path("senses").get(0).path("meanings");
        for (int i = 0; i < 30; i++) {
            meanings.add(objectMapper.createObjectNode().put("definitionEn", "x").put("definitionZh", ""));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.validate(tooManyMeanings));

        ObjectNode tooLong = codec.fromLegacy("record", VocabularyTestFixtures.legacyVocabularyContent(objectMapper));
        ((ObjectNode) tooLong.path("senses").get(0).path("meanings").get(0))
                .put("definitionEn", IntStream.range(0, 2001).mapToObj(ignored -> "x").collect(Collectors.joining()));
        assertThrows(IllegalArgumentException.class, () -> codec.validate(tooLong));
    }
}
