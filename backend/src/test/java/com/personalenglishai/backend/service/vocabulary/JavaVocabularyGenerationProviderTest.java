package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JavaVocabularyGenerationProviderTest {

    @Mock
    private OpenAiClient ai;
    @Mock
    private VocabularyGenerationCache cache;
    @Mock
    private VocabularyCoreFallbackGenerator fallback;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyCoreContentCodec codec;
    private JavaVocabularyGenerationProvider provider;

    @BeforeEach
    void setUp() {
        codec = new VocabularyCoreContentCodec(objectMapper);
        provider = new JavaVocabularyGenerationProvider(
                ai, cache, codec, fallback, new VocabularyMarkdownPromptBuilder(objectMapper));
        lenient().when(cache.key(anyString(), anyInt(), any(JsonNode.class), anyString()))
                .thenReturn("cache-key");
        lenient().when(cache.get(anyString())).thenReturn(Optional.empty());
        lenient().when(ai.getModel()).thenReturn("test-model");
    }

    @Test
    void dictionaryBackedCardUsesOneMarkdownCallAndKeepsDictionaryCore() {
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-1"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n\nKeep a record of your work.");

        GeneratedVocabularyCard result = provider.generate(dictionaryInput("The record was complete.", "trace-1"));

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
        ObjectNode fallbackCore = objectMapper.readValue(validCore(), ObjectNode.class);
        when(fallback.generate("record", "", "trace-2")).thenReturn(fallbackCore);
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-2"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n\nRecord the result.");

        GeneratedVocabularyCard result = provider.generate(emptyInput("", "trace-2"));

        assertFalse(result.partial());
        verify(fallback).generate("record", "", "trace-2");
        verify(ai).callWithTraceId(anyString(), anyString(), eq("trace-2"), eq(0.2), eq(1200));
    }

    @Test
    void markdownFailureReturnsValidatedDictionaryCoreAsPartial() {
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-partial"), eq(0.2), eq(1200)))
                .thenThrow(new RuntimeException("upstream unavailable"));

        GeneratedVocabularyCard partial = provider.generate(dictionaryInput("", "trace-partial"));

        assertTrue(partial.partial());
        assertEquals("record", partial.core().path("term").asText());
        assertEquals("", partial.markdown());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void rawHtmlMarkdownIsRejectedAsPartial() {
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-html"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n<script>alert('x')</script>");

        GeneratedVocabularyCard partial = provider.generate(dictionaryInput("", "trace-html"));

        assertTrue(partial.partial());
        assertEquals("", partial.markdown());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!-- hidden -->",
            "<!DOCTYPE html>",
            "<!ENTITY example 'value'>",
            "<?xml version='1.0'?>",
            "<section>",
            "</section>",
            "<br />"
    })
    void everyRawHtmlFormIsRejectedAsPartial(String rawHtml) {
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-html-forms"), eq(0.2), eq(1200)))
                .thenReturn("## Usage\n\n" + rawHtml);

        GeneratedVocabularyCard partial = provider.generate(dictionaryInput("", "trace-html-forms"));

        assertTrue(partial.partial());
        assertEquals("", partial.markdown());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void unavailableFallbackCoreRaisesRetryableStableError() {
        when(fallback.generate("record", "", "trace-core-fail"))
                .thenThrow(new IllegalArgumentException("bad model output"));

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> provider.generate(emptyInput("", "trace-core-fail")));

        assertEquals("CORE_CONTENT_UNAVAILABLE", exception.code());
        assertTrue(exception.retryable());
        verifyNoInteractions(ai);
    }

    @Test
    void returnsValidatedCachedCoreAndMarkdownWithoutAiCalls() {
        ObjectNode core = codec.fromDictionary("record", VocabularyTestFixtures.dictionaryLookupWithCoreTruth());
        when(cache.get("cache-key")).thenReturn(Optional.of(
                new VocabularyGenerationCache.CachedGeneration(core, "## Cached")));

        GeneratedVocabularyCard result = provider.generate(dictionaryInput("", "trace-cache"));

        assertEquals("## Cached", result.markdown());
        assertEquals("cache", result.model());
        verifyNoInteractions(ai, fallback);
    }

    @Test
    void validButSemanticallyPoisonedCachedCoreIsEvictedAndCannotReplaceDictionaryTruth() throws Exception {
        ObjectNode poisonedCore = objectMapper.readValue(validCore(), ObjectNode.class);
        ((ObjectNode) poisonedCore.path("senses").get(0).path("meanings").get(0))
                .put("definitionEn", "poisoned cached definition");
        when(cache.get("cache-key")).thenReturn(Optional.of(
                new VocabularyGenerationCache.CachedGeneration(poisonedCore, "## Cached")));
        when(ai.callWithTraceId(anyString(), anyString(), eq("trace-poisoned-cache"), eq(0.2), eq(1200)))
                .thenReturn("## Fresh Markdown");

        GeneratedVocabularyCard result = provider.generate(dictionaryInput("", "trace-poisoned-cache"));

        assertEquals("a written account", result.core().path("senses").get(0)
                .path("meanings").get(0).path("definitionEn").asText());
        assertEquals("## Fresh Markdown", result.markdown());
        verify(cache).evict("cache-key");
    }

    private VocabularyGenerationInput dictionaryInput(String sourceContext, String traceId) {
        return new VocabularyGenerationInput(
                "record", codec.fromDictionary("record", VocabularyTestFixtures.dictionaryLookupWithCoreTruth()),
                sourceContext, theme(), traceId);
    }

    private VocabularyGenerationInput emptyInput(String sourceContext, String traceId) {
        return new VocabularyGenerationInput(
                "record", codec.fromDictionary("record", null), sourceContext, theme(), traceId);
    }

    private ResolvedVocabularyTheme theme() {
        return new ResolvedVocabularyTheme(
                "theme_system_basic", 1, "Theme", "", "basic-markdown-v1", 1, "basic");
    }

    private String validCore() {
        return """
                {"schemaVersion":1,"term":"record","phonetics":[],"senses":[
                  {"partOfSpeech":"noun","meanings":[
                    {"definitionEn":"a written account","definitionZh":"记录"}]}]}
                """;
    }
}
