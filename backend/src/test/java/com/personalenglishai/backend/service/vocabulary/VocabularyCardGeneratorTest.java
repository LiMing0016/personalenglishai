package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyTemplateRegistry registry;
    private VocabularyCardGenerator generator;

    @BeforeEach
    void setUp() {
        registry = new VocabularyTemplateRegistry(objectMapper);
        generator = new VocabularyCardGenerator(
                new VocabularyDictionaryEnricher(dictionary), ai, cache, registry, objectMapper);
        lenient().when(cache.key(anyString(), anyString(), anyInt(), any(), anyString()))
                .thenReturn("cache-key");
        lenient().when(cache.get(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void preservesAllAvailableDictionaryTruth() {
        DictionaryLookupResponse dictionaryData = dictionaryLookupWithAllFields();
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(dictionaryData);
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_1"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"invented","phonetic":"/wrong/","partOfSpeech":"noun",\
                        "definitions":["wrong"],"examples":["Wrong example."],"notes":""}
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"),
                List.of(VocabularyTestFixtures.manualSource("The company is innovative.")),
                registry.require("basic"),
                "job_1");

        assertEquals("innovative", result.content().path("term").asText());
        assertEquals("/ˈɪnəveɪtɪv/", result.content().path("phonetic").asText());
        assertEquals("adjective", result.content().path("partOfSpeech").asText());
        assertEquals("introducing new ideas", result.content().path("definitions").get(0).asText());
        assertEquals("The company introduced an innovative product.",
                result.content().path("examples").get(0).asText());
        verify(dictionary).lookup("innovative", "en-gb");
        verify(cache).put(anyString(), eq(result.content()), eq(Duration.ofDays(7)));
    }

    @Test
    void copiesCapturedReadingContextInsteadOfAiContext() {
        when(dictionary.lookup("innovative", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookup(
                        "innovative", "adjective", "introducing new ideas"));
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_context"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","definitions":["wrong"],"sourceContext":"invented context",\
                        "contextExplanation":"Used to describe novelty.","paraphrases":[],"notes":""}
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"),
                List.of(VocabularyTestFixtures.manualSource("The company is innovative.")),
                registry.require("reading"),
                "job_context");

        assertEquals("The company is innovative.", result.content().path("sourceContext").asText());
    }

    @Test
    void keepsReadingContextEmptyWhenNothingWasCaptured() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_empty_context"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","definitions":[],"sourceContext":"invented context",\
                        "contextExplanation":"","paraphrases":[],"notes":""}
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"),
                List.of(),
                registry.require("reading"),
                "job_empty_context");

        assertEquals("", result.content().path("sourceContext").asText());
    }

    @Test
    void rejectsMalformedStructuredOutputWithoutCachingIt() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("not-json The company is innovative.");

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> generator.generate(
                        VocabularyTestFixtures.generating("innovative"),
                        List.of(VocabularyTestFixtures.manualSource("The company is innovative.")),
                        registry.require("basic"),
                        "job_2"));

        assertEquals("INVALID_AI_OUTPUT", exception.code());
        assertTrue(exception.retryable());
        assertFalse(exception.getMessage().contains("The company is innovative."));
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void continuesAiGenerationWhenDictionaryDoesNotContainTerm() {
        when(dictionary.lookup("innovative", "en-gb"))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.NOT_FOUND));
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_not_found"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","phonetic":"/ai/","partOfSpeech":"adjective",\
                        "definitions":["AI definition"],"examples":[],"notes":""}
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_not_found");

        assertEquals("AI definition", result.content().path("definitions").get(0).asText());
        verify(ai).callWithTraceId(anyString(), anyString(), eq("job_not_found"), eq(0.2), eq(1200));
    }

    @Test
    void reportsOperationalDictionaryErrorsAsRetryable() {
        when(dictionary.lookup("innovative", "en-gb"))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.TIMEOUT));

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> generator.generate(
                        VocabularyTestFixtures.generating("innovative"), List.of(),
                        registry.require("basic"), "job_dictionary_timeout"));

        assertEquals("DICTIONARY_LOOKUP_FAILED", exception.code());
        assertTrue(exception.retryable());
        verifyNoInteractions(ai);
    }

    @Test
    void rejectsJsonThatIsNotAnObject() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("[]");

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> generator.generate(
                        VocabularyTestFixtures.generating("innovative"), List.of(),
                        registry.require("basic"), "job_array"));

        assertEquals("INVALID_AI_OUTPUT", exception.code());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void rejectsOutputThatFailsTemplateValidation() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"term\":\"innovative\",\"definitions\":[]}");

        assertThrows(VocabularyGenerationException.class, () -> generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_template"));

        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void usesValidatedCacheBeforeCallingAi() throws Exception {
        when(dictionary.lookup("innovative", "en-gb"))
                .thenReturn(VocabularyTestFixtures.dictionaryLookup(
                        "innovative", "adjective", "introducing new ideas"));
        JsonNode cached = objectMapper.readTree("""
                {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                "definitions":[],"examples":[],"notes":""}
                """);
        when(cache.get(anyString())).thenReturn(Optional.of(cached));

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_3");

        assertEquals("introducing new ideas", result.content().path("definitions").get(0).asText());
        assertTrue(cached.path("definitions").isEmpty());
        assertEquals("cache", result.model());
        verifyNoInteractions(ai);
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void ignoresTemplateInvalidCacheAndReplacesItWithValidatedOutput() throws Exception {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        JsonNode invalidCached = objectMapper.readTree("{\"term\":\"innovative\"}");
        when(cache.get(anyString())).thenReturn(Optional.of(invalidCached));
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_stale"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                        "definitions":[],"examples":[],"notes":""}
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_stale");

        assertEquals("innovative", result.content().path("term").asText());
        verify(ai).callWithTraceId(anyString(), anyString(), eq("job_stale"), eq(0.2), eq(1200));
        verify(cache).put(anyString(), eq(result.content()), eq(Duration.ofDays(7)));
    }

    @Test
    void reappliesCapturedContextToCachedReadingCardWithoutMutatingCacheValue() throws Exception {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        JsonNode mismatchedCache = objectMapper.readTree("""
                {"term":"innovative","definitions":[],"sourceContext":"invented context",\
                "contextExplanation":"","paraphrases":[],"notes":""}
                """);
        when(cache.get(anyString())).thenReturn(Optional.of(mismatchedCache));

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"),
                List.of(VocabularyTestFixtures.manualSource("Captured context.")),
                registry.require("reading"),
                "job_cache_context");

        assertEquals("Captured context.", result.content().path("sourceContext").asText());
        assertEquals("invented context", mismatchedCache.path("sourceContext").asText());
        assertEquals("cache", result.model());
        verifyNoInteractions(ai);
    }

    @Test
    void reappliesCurrentDictionaryTruthToCachedCard() throws Exception {
        DictionaryLookupResponse dictionaryData = dictionaryLookupWithAllFields();
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(dictionaryData);
        JsonNode cached = objectMapper.readTree("""
                {"term":"innovative","phonetic":"/stale/","partOfSpeech":"noun",\
                "definitions":["stale definition"],"examples":["Stale example."],"notes":""}
                """);
        when(cache.get(anyString())).thenReturn(Optional.of(cached));

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_cache_dictionary");

        assertEquals("/ˈɪnəveɪtɪv/", result.content().path("phonetic").asText());
        assertEquals("adjective", result.content().path("partOfSpeech").asText());
        assertEquals("introducing new ideas", result.content().path("definitions").get(0).asText());
        assertEquals("The company introduced an innovative product.",
                result.content().path("examples").get(0).asText());
        assertEquals("/stale/", cached.path("phonetic").asText());
        verifyNoInteractions(ai);
    }

    @Test
    void evictsPoisonedCacheAndRegeneratesValidatedContent() throws Exception {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        JsonNode poisoned = objectMapper.readTree("""
                {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                "definitions":[],"examples":[],"notes":"","unexpected":"poison"}
                """);
        when(cache.get(anyString())).thenReturn(Optional.of(poisoned));
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_poisoned"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                        "definitions":[],"examples":[],"notes":""}
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_poisoned");

        assertFalse(result.content().has("unexpected"));
        verify(cache).evict("cache-key");
        verify(ai).callWithTraceId(anyString(), anyString(), eq("job_poisoned"), eq(0.2), eq(1200));
        verify(cache).put("cache-key", result.content(), Duration.ofDays(7));
    }

    @Test
    void acceptsJsonWrappedInMarkdownFence() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_fenced"), eq(0.2), eq(1200)))
                .thenReturn("""
                        ```json
                        {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                        "definitions":[],"examples":[],"notes":""}
                        ```
                        """);

        GeneratedVocabularyCard result = generator.generate(
                VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_fenced");

        assertEquals("innovative", result.content().path("term").asText());
    }

    @Test
    void rejectsTrailingJsonTokensWithoutCaching() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_trailing"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                        "definitions":[],"examples":[],"notes":""} {}
                        """);

        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> generator.generate(
                        VocabularyTestFixtures.generating("innovative"), List.of(),
                        registry.require("basic"), "job_trailing"));

        assertEquals("INVALID_AI_OUTPUT", exception.code());
        verify(cache, never()).put(anyString(), any(), any());
    }

    @Test
    void promptNamesTheExactTemplateFields() {
        when(dictionary.lookup("innovative", "en-gb")).thenReturn(null);
        when(ai.callWithTraceId(anyString(), anyString(), eq("job_prompt"), eq(0.2), eq(1200)))
                .thenReturn("""
                        {"term":"innovative","phonetic":"","partOfSpeech":"adjective",\
                        "definitions":[],"examples":[],"notes":""}
                        """);

        generator.generate(VocabularyTestFixtures.generating("innovative"), List.of(),
                registry.require("basic"), "job_prompt");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(ai).callWithTraceId(systemPrompt.capture(), anyString(), eq("job_prompt"), eq(0.2), eq(1200));
        assertTrue(systemPrompt.getValue().contains(
                "term, phonetic, partOfSpeech, definitions, examples, notes"));
        assertTrue(systemPrompt.getValue().contains("one JSON object"));
    }

    @Test
    void basicFixtureHasConcreteGeneratedCardType() {
        GeneratedVocabularyCard fixture = VocabularyTestFixtures.basicGeneratedCard();

        assertEquals("test-model", fixture.model());
    }

    private DictionaryLookupResponse dictionaryLookupWithAllFields() {
        DictionaryLookupResponse response = VocabularyTestFixtures.dictionaryLookup(
                "innovative", "adjective", "introducing new ideas");
        response.setPhonetics(List.of(new DictionaryPhoneticDto("/ˈɪnəveɪtɪv/", null)));
        DictionaryEntryDto entry = response.getEntries().get(0);
        entry.setExamples(List.of("The company introduced an innovative product."));
        return response;
    }
}
