package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VocabularyCardGeneratorTest {

    @Mock
    private DictionaryLookupService dictionary;
    @Mock
    private OpenAiClient ai;
    @Mock
    private VocabularyGenerationCache cache;
    @Mock
    private VocabularyCoreFallbackGenerator fallback;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyCoreContentCodec codec;
    private VocabularyCardGenerator generator;

    @BeforeEach
    void setUp() {
        codec = new VocabularyCoreContentCodec(objectMapper);
        generator = new VocabularyCardGenerator(
                new VocabularyDictionaryEnricher(dictionary), ai, cache, codec, fallback,
                new VocabularyMarkdownPromptBuilder(objectMapper));
        lenient().when(cache.key(anyString(), anyInt(), any(JsonNode.class), anyString()))
                .thenReturn("cache-key");
        lenient().when(cache.get(anyString())).thenReturn(Optional.empty());
        lenient().when(ai.getModel()).thenReturn("test-model");
    }

    @Test
    void dictionaryBackedCardUsesOneMarkdownCallAndKeepsDictionaryCore() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-1"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n\nKeep a record of your work.");

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("record"),
                List.of(VocabularyTestFixtures.manualSource("The record was complete.")),
                theme("theme_system_basic", 1, "basic-markdown-v1", ""), "trace-1");

        assertEquals("record", result.core().path("term").asText());
        assertEquals("a written account", result.core().path("senses").get(0)
                .path("meanings").get(0).path("definitionEn").asText());
        assertEquals("## Usage\n\nKeep a record of your work.", result.markdown());
        assertEquals(1, result.contentFormatVersion());
        assertFalse(result.partial());
        verifyNoInteractions(fallback);
        verify(ai).callWithTraceId(anyString(), anyString(), eq("trace-1"), eq(0.2), eq(1200));
        verify(cache).put(eq("cache-key"), any(VocabularyGenerationCache.CachedGeneration.class),
                eq(Duration.ofDays(7)));
    }

    @Test
    void dictionaryMissingCardUsesOneFallbackAndOneMarkdownCall() throws Exception {
        when(dictionary.lookup("record", "en-gb")).thenReturn(null);
        ObjectNode fallbackCore = objectMapper.readValue(validCore(), ObjectNode.class);
        when(fallback.generate("record", "", "trace-2")).thenReturn(fallbackCore);
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-2"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n\nRecord the result.");

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("record"), List.of(),
                theme("theme_system_basic", 1, "basic-markdown-v1", ""), "trace-2");

        assertFalse(result.partial());
        verify(fallback).generate("record", "", "trace-2");
        verify(ai).callWithTraceId(anyString(), anyString(), eq("trace-2"), eq(0.2), eq(1200));
    }

    @Test
    void markdownFailureReturnsValidatedDictionaryCoreAsPartial() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-partial"), eq(0.2), eq(1200)))
                .thenThrow(new RuntimeException("upstream unavailable"));

        GeneratedVocabularyCard partial = generator.generate(
                VocabularyTestFixtures.generating("record"), List.of(),
                theme("theme_custom", 4, "custom-markdown-v1", "exam focus"), "trace-partial");

        assertTrue(partial.partial());
        assertEquals("record", partial.core().path("term").asText());
        assertEquals("", partial.markdown());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void rawHtmlMarkdownIsRejectedAsPartial() {
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-html"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n<script>alert('x')</script>");

        GeneratedVocabularyCard partial = generator.generate(
                VocabularyTestFixtures.generating("record"), List.of(),
                theme("theme_system_basic", 1, "basic-markdown-v1", ""), "trace-html");

        assertTrue(partial.partial());
        assertEquals("", partial.markdown());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void unavailableFallbackCoreRaisesRetryableStableError() {
        when(dictionary.lookup("record", "en-gb")).thenReturn(null);
        when(fallback.generate("record", "", "trace-core-fail"))
                .thenThrow(new IllegalArgumentException("bad model output"));

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> generator.generate(VocabularyTestFixtures.generating("record"), List.of(),
                        theme("theme_system_basic", 1, "basic-markdown-v1", ""),
                        "trace-core-fail"));

        assertEquals("CORE_CONTENT_UNAVAILABLE", exception.code());
        assertTrue(exception.retryable());
        verifyNoInteractions(ai);
    }

    @Test
    void operationalDictionaryFailureDoesNotCallAi() {
        when(dictionary.lookup("record", "en-gb"))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.TIMEOUT));

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> generator.generate(VocabularyTestFixtures.generating("record"), List.of(),
                        theme("theme_system_basic", 1, "basic-markdown-v1", ""), "trace-dict"));

        assertEquals("DICTIONARY_LOOKUP_FAILED", exception.code());
        verifyNoInteractions(ai, fallback);
    }

    @Test
    void returnsValidatedCachedCoreAndMarkdownWithoutAiCalls() throws Exception {
        ObjectNode core = objectMapper.readValue(validCore(), ObjectNode.class);
        when(dictionary.lookup("record", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        when(cache.get("cache-key")).thenReturn(Optional.of(
                new VocabularyGenerationCache.CachedGeneration(core, "## Cached")));

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("record"), List.of(),
                theme("theme_system_basic", 1, "basic-markdown-v1", ""), "trace-cache");

        assertEquals("## Cached", result.markdown());
        assertEquals("cache", result.model());
        verifyNoInteractions(ai, fallback);
    }

    private ResolvedVocabularyTheme theme(String uid, int version, String strategy, String purpose) {
        return new ResolvedVocabularyTheme(uid, version, "Theme", purpose, strategy, 1, "basic");
    }

    private String validCore() {
        return """
                {"schemaVersion":1,"term":"record","phonetics":[],"senses":[
                  {"partOfSpeech":"noun","meanings":[
                    {"definitionEn":"a written account","definitionZh":"记录"}]}]}
                """;
    }
}
