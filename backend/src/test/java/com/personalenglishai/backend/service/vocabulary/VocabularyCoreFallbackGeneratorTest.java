package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyCoreFallbackGeneratorTest {

    @Mock
    private OpenAiClient ai;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyCoreFallbackGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new VocabularyCoreFallbackGenerator(
                ai, new VocabularyCoreContentCodec(objectMapper), objectMapper);
    }

    @Test
    void callsStrictStructuredOutputAndOverwritesTermFromCardIdentity() {
        when(ai.callStructuredWithTraceId(
                anyString(), anyString(), eq("trace-core"), eq("vocabulary_core_v1"),
                any(JsonNode.class), eq(0.0), eq(1200)))
                .thenReturn(validCore("invented"));

        ObjectNode core = generator.generate("record", "The record was complete.", "trace-core");

        assertEquals("record", core.path("term").asText());
        verify(ai).callStructuredWithTraceId(
                anyString(), anyString(), eq("trace-core"), eq("vocabulary_core_v1"),
                any(JsonNode.class), eq(0.0), eq(1200));
    }

    @Test
    void schemaIsClosedAtEveryObjectAndMatchesCodecLimits() {
        when(ai.callStructuredWithTraceId(
                anyString(), anyString(), eq("trace-schema"), eq("vocabulary_core_v1"),
                any(JsonNode.class), eq(0.0), eq(1200)))
                .thenReturn(validCore("record"));

        generator.generate("record", "", "trace-schema");

        ArgumentCaptor<JsonNode> schema = ArgumentCaptor.forClass(JsonNode.class);
        verify(ai).callStructuredWithTraceId(
                anyString(), anyString(), eq("trace-schema"), eq("vocabulary_core_v1"),
                schema.capture(), eq(0.0), eq(1200));
        JsonNode value = schema.getValue();
        assertTrue(!value.path("additionalProperties").asBoolean());
        assertEquals(10, value.path("properties").path("phonetics").path("maxItems").asInt());
        assertEquals(20, value.path("properties").path("senses").path("maxItems").asInt());
        assertEquals(30, value.path("properties").path("senses").path("items")
                .path("properties").path("meanings").path("maxItems").asInt());
        assertEquals(2000, value.path("properties").path("term").path("maxLength").asInt());
        assertTrue(!value.path("properties").path("phonetics").path("items")
                .path("additionalProperties").asBoolean());
        assertTrue(!value.path("properties").path("senses").path("items")
                .path("additionalProperties").asBoolean());
        assertTrue(!value.path("properties").path("senses").path("items")
                .path("properties").path("meanings").path("items")
                .path("additionalProperties").asBoolean());
        assertTrue(value.path("properties").path("phonetics").path("items")
                .path("properties").path("audioUrl").path("type").isArray());
    }

    @Test
    void rejectsTrailingTokensAndCodecInvalidOutput() {
        when(ai.callStructuredWithTraceId(
                anyString(), anyString(), anyString(), eq("vocabulary_core_v1"),
                any(JsonNode.class), eq(0.0), eq(1200)))
                .thenReturn(validCore("record") + " {}");

        assertThrows(IllegalArgumentException.class,
                () -> generator.generate("record", "", "trace-invalid"));
    }

    private String validCore(String term) {
        return """
                {"schemaVersion":1,"term":"%s","phonetics":[
                  {"region":"uk","text":"/rekord/","audioUrl":null}],
                 "senses":[{"partOfSpeech":"noun","meanings":[
                  {"definitionEn":"a written account","definitionZh":"记录"}]}]}
                """.formatted(term);
    }
}
