package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class VocabularyCoreContentCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VocabularyCoreContentCodec codec = new VocabularyCoreContentCodec(objectMapper);

    @Test
    void dictionaryTruthKeepsOnePhoneticPerStandardRegionAndGroupsMeaningsByPartOfSpeech() {
        DictionaryLookupResponse dictionary = VocabularyTestFixtures.dictionaryLookupWithCoreTruth();

        ObjectNode core = codec.fromDictionary("record", dictionary);

        assertEquals(2, core.path("schemaVersion").asInt());
        assertEquals("record", core.path("term").asText());
        assertEquals(2, core.path("phonetics").size());
        assertEquals("uk", core.path("phonetics").get(0).path("region").asText());
        assertEquals("us", core.path("phonetics").get(1).path("region").asText());
        assertEquals(2, core.path("senses").size());
        assertEquals("noun", core.path("senses").get(0).path("partOfSpeech").asText());
        assertEquals("sense_1", core.path("senses").get(0).path("id").asText());
        assertEquals("meaning_1_1", core.path("senses").get(0).path("meanings").get(0).path("id").asText());
        assertEquals(3, core.path("senses").get(0).path("meanings").size());
        assertEquals("a written account", core.path("senses").get(0).path("meanings").get(0).path("definitionEn").asText());
        assertEquals("记录", core.path("senses").get(0).path("meanings").get(0).path("definitionZh").asText());
        assertEquals("verb", core.path("senses").get(1).path("partOfSpeech").asText());
        codec.validate("record", core);
    }

    @Test
    void dictionaryProjectionKeepsOnlyTheFirstUnlabelledPronunciation() {
        DictionaryLookupResponse dictionary = new DictionaryLookupResponse();
        dictionary.setLanguage("en-gb");
        dictionary.setPhonetics(List.of(
                new DictionaryPhoneticDto("ɪnˈspaɪə(r)", null),
                new DictionaryPhoneticDto("ɪnˈspaɪəz", null),
                new DictionaryPhoneticDto("ɪnˈspaɪəd", null)));
        dictionary.setEntries(List.of());

        ObjectNode core = codec.fromDictionary("inspire", dictionary);

        assertEquals(1, core.path("phonetics").size());
        assertEquals("uk", core.path("phonetics").get(0).path("region").asText());
        assertEquals("ɪnˈspaɪə(r)", core.path("phonetics").get(0).path("text").asText());
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
    void dictionaryProjectionBoundsPhoneticsSensesAndMeaningsToCodecLimits() {
        DictionaryLookupResponse dictionary = new DictionaryLookupResponse();
        dictionary.setLanguage("en");
        dictionary.setPhonetics(IntStream.range(0, 11)
                .mapToObj(i -> new DictionaryPhoneticDto("/p" + i + "/", null))
                .collect(Collectors.toList()));
        dictionary.setEntries(IntStream.range(0, 21)
                .mapToObj(i -> {
                    DictionaryEntryDto entry = new DictionaryEntryDto("pos-" + i);
                    entry.setDefinitions(List.of("definition-" + i));
                    return entry;
                })
                .collect(Collectors.toList()));
        DictionaryEntryDto manyMeanings = new DictionaryEntryDto("many-meanings");
        manyMeanings.setDefinitions(IntStream.range(0, 31)
                .mapToObj(i -> "meaning-" + i)
                .collect(Collectors.toList()));
        dictionary.setEntries(new ArrayList<>(dictionary.getEntries()));
        dictionary.getEntries().set(19, manyMeanings);

        ObjectNode core = codec.fromDictionary("bounded", dictionary);

        assertEquals(1, core.path("phonetics").size());
        assertEquals(20, core.path("senses").size());
        assertEquals(30, core.path("senses").get(19).path("meanings").size());
        codec.validate("bounded", core);
    }

    @Test
    void dictionaryProjectionRejectsScalarValuesOverTwoThousandCharacters() {
        DictionaryLookupResponse dictionary = VocabularyTestFixtures.dictionaryLookup(
                "bounded", "noun", "x".repeat(2001));

        assertThrows(IllegalArgumentException.class, () -> codec.fromDictionary("bounded", dictionary));
    }

    @Test
    void legacyProjectionMapsScalarFieldsWithoutMutatingLegacyContent() {
        ObjectNode legacy = VocabularyTestFixtures.legacyVocabularyContent(objectMapper);
        JsonNode snapshot = legacy.deepCopy();

        ObjectNode core = codec.fromLegacy("record", legacy);

        assertEquals(2, core.path("schemaVersion").asInt());
        assertEquals("record", core.path("term").asText());
        assertEquals("/ˈrekɔːd/", core.path("phonetics").get(0).path("text").asText());
        assertEquals("noun", core.path("senses").get(0).path("partOfSpeech").asText());
        assertEquals("sense_1", core.path("senses").get(0).path("id").asText());
        assertEquals("记录", core.path("senses").get(0).path("meanings").get(0).path("definitionZh").asText());
        assertEquals(snapshot, legacy);
        assertFalse(core.has("definitions"));
    }

    @Test
    void legacyProjectionRetainsDefinitionsUnderUnknownPartOfSpeech() {
        ObjectNode legacy = objectMapper.createObjectNode();
        legacy.putArray("definitions").add("a saved entry；一条保存的记录");

        ObjectNode core = codec.fromLegacy("record", legacy);

        assertEquals("unknown", core.path("senses").get(0).path("partOfSpeech").asText());
        assertEquals("a saved entry", core.path("senses").get(0).path("meanings").get(0).path("definitionEn").asText());
        codec.validate("record", core);
    }

    @Test
    void legacyProjectionRejectsScalarValuesOverTwoThousandCharacters() {
        ObjectNode legacy = objectMapper.createObjectNode();
        legacy.put("phonetic", "x".repeat(2001));

        assertThrows(IllegalArgumentException.class, () -> codec.fromLegacy("record", legacy));
    }

    @Test
    void legacyProjectionRejectsOversizedCallerTermForEmptyLegacyInputs() {
        String oversizedTerm = "x".repeat(2001);

        assertThrows(IllegalArgumentException.class, () -> codec.fromLegacy(oversizedTerm, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.fromLegacy(oversizedTerm, objectMapper.getNodeFactory().textNode("legacy")));
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
    void validationKeepsSchemaOneReadableButRequiresIdsForSchemaTwo() throws Exception {
        JsonNode schemaOne = objectMapper.readTree("""
                {
                  "schemaVersion": 1,
                  "term": "record",
                  "phonetics": [],
                  "senses": [{
                    "partOfSpeech": "noun",
                    "meanings": [{"definitionEn": "a written account", "definitionZh": "记录"}]
                  }]
                }
                """);
        codec.validate("record", schemaOne);

        ObjectNode missingIds = (ObjectNode) schemaOne.deepCopy();
        missingIds.put("schemaVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> codec.validate("record", missingIds));
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
