package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VocabularyTemplateRegistryTest {

    private final VocabularyTemplateRegistry registry = new VocabularyTemplateRegistry(new ObjectMapper());

    @Test
    void exposesStableBuiltInTemplates() {
        assertEquals(List.of("basic", "exam", "reading"),
                registry.list().stream().map(VocabularyTemplateResponse::key).toList());
        assertEquals(1, registry.require("basic").version());
        assertEquals(List.of("term", "phonetic", "partOfSpeech", "definitions", "examples", "notes"),
                registry.require("basic").requiredFields());
        assertEquals(1, registry.require("exam").version());
        assertEquals(List.of("term", "phonetic", "partOfSpeech", "definitions", "examTips", "collocations", "examples", "notes"),
                registry.require("exam").requiredFields());
        assertEquals(1, registry.require("reading").version());
        assertEquals(List.of("term", "definitions", "sourceContext", "contextExplanation", "paraphrases", "notes"),
                registry.require("reading").requiredFields());
    }

    @Test
    void rejectsContentMissingRequiredBasicFields() {
        JsonNode invalid = new ObjectMapper().createObjectNode().put("term", "innovative");
        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }

    @Test
    void rejectsUnexpectedFields() {
        ObjectNode invalid = validBasic().put("unexpected", "value");

        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }

    @Test
    void validatesEveryRequiredFieldTypeForEveryTemplate() {
        Map<String, ObjectNode> validContent = Map.of(
                "basic", validBasic(),
                "exam", validExam(),
                "reading", validReading());
        Set<String> arrayFields = Set.of(
                "definitions", "examples", "examTips", "collocations", "paraphrases");

        validContent.forEach((templateKey, content) -> {
            assertDoesNotThrow(() -> registry.validate(templateKey, content));
            registry.require(templateKey).requiredFields().forEach(field -> {
                ObjectNode invalid = content.deepCopy();
                if (arrayFields.contains(field)) {
                    invalid.put(field, "wrong type");
                } else {
                    invalid.putArray(field);
                }
                assertThrows(IllegalArgumentException.class,
                        () -> registry.validate(templateKey, invalid),
                        templateKey + "." + field);
            });
        });
    }

    @Test
    void rejectsNonStringArrayItems() {
        ObjectNode invalid = validBasic();
        invalid.withArray("definitions").addObject().put("text", "not a string");

        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }

    @Test
    void rejectsOversizedTextScalar() {
        ObjectNode invalid = validBasic().put("notes", "x".repeat(2001));

        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }

    @Test
    void rejectsOversizedArrayItem() {
        ObjectNode invalid = validBasic();
        invalid.withArray("definitions").add("x".repeat(501));

        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }

    @Test
    void rejectsExcessiveArrayItems() {
        ObjectNode invalid = validBasic();
        for (int index = 0; index < 51; index++) {
            invalid.withArray("definitions").add("definition " + index);
        }

        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }

    private ObjectNode validBasic() {
        ObjectNode content = new ObjectMapper().createObjectNode();
        content.put("term", "innovative");
        content.put("phonetic", "/ˈɪnəveɪtɪv/");
        content.put("partOfSpeech", "adjective");
        content.putArray("definitions").add("introducing new ideas");
        content.putArray("examples").add("The company is innovative.");
        content.put("notes", "");
        return content;
    }

    private ObjectNode validExam() {
        ObjectNode content = validBasic();
        content.putArray("examTips").add("Use it to describe a new method.");
        content.putArray("collocations").add("innovative approach");
        return content;
    }

    private ObjectNode validReading() {
        ObjectNode content = new ObjectMapper().createObjectNode();
        content.put("term", "innovative");
        content.putArray("definitions").add("introducing new ideas");
        content.put("sourceContext", "The company is innovative.");
        content.put("contextExplanation", "It describes the company's approach.");
        content.putArray("paraphrases").add("The company introduces new ideas.");
        content.put("notes", "");
        return content;
    }
}
