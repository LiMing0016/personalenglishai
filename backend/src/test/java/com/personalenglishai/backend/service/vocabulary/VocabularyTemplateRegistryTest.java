package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyTemplateResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class VocabularyTemplateRegistryTest {

    private final VocabularyTemplateRegistry registry = new VocabularyTemplateRegistry(new ObjectMapper());

    @Test
    void exposesStableBuiltInTemplates() {
        assertEquals(List.of("basic", "exam", "reading"),
                registry.list().stream().map(VocabularyTemplateResponse::key).toList());
        assertEquals(1, registry.require("basic").version());
    }

    @Test
    void rejectsContentMissingRequiredBasicFields() {
        JsonNode invalid = new ObjectMapper().createObjectNode().put("term", "innovative");
        assertThrows(IllegalArgumentException.class, () -> registry.validate("basic", invalid));
    }
}
