package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.support.VocabularyTestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class VocabularyGenerationCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VocabularyGenerationCache cache;

    @BeforeEach
    void setUp() {
        cache = new VocabularyGenerationCache(redisTemplate, objectMapper);
    }

    @Test
    void keyHashesTermTemplateDictionaryContentAndContext() {
        DictionaryLookupResponse firstDictionary = VocabularyTestFixtures.dictionaryLookup(
                "innovative", "adjective", "introducing new ideas");
        DictionaryLookupResponse changedDictionary = VocabularyTestFixtures.dictionaryLookup(
                "innovative", "adjective", "using new ideas or methods");

        String base = cache.key("innovative", "reading", 1, firstDictionary, "captured sentence one");

        assertTrue(base.matches("vocabulary:generation:v1:[0-9a-f]{64}"));
        assertNotEquals(base, cache.key("innovation", "reading", 1, firstDictionary, "captured sentence one"));
        assertNotEquals(base, cache.key("innovative", "basic", 1, firstDictionary, "captured sentence one"));
        assertNotEquals(base, cache.key("innovative", "reading", 2, firstDictionary, "captured sentence one"));
        assertNotEquals(base, cache.key("innovative", "reading", 1, changedDictionary, "captured sentence one"));
        assertNotEquals(base, cache.key("innovative", "reading", 1, firstDictionary, "captured sentence two"));
    }

    @Test
    void ignoresDictionaryUserStateWhenHashingContent() {
        DictionaryLookupResponse first = VocabularyTestFixtures.dictionaryLookup(
                "innovative", "adjective", "introducing new ideas");
        DictionaryLookupResponse second = VocabularyTestFixtures.dictionaryLookup(
                "innovative", "adjective", "introducing new ideas");
        second.setFavorite(true);
        second.setLookupCount(99);

        assertEquals(
                cache.key("innovative", "basic", 1, first, ""),
                cache.key("innovative", "basic", 1, second, ""));
    }

    @Test
    void storesJsonWithRequestedSevenDayTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        JsonNode content = objectMapper.readTree("{\"term\":\"innovative\"}");

        cache.put("cache-key", content, Duration.ofDays(7));

        verify(valueOperations).set("cache-key", "{\"term\":\"innovative\"}", Duration.ofDays(7));
    }

    @Test
    void treatsRedisReadExceptionAsCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache-key")).thenThrow(new IllegalStateException("redis unavailable"));

        assertTrue(cache.get("cache-key").isEmpty());
    }

    @Test
    void evictsMalformedSerializedContent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache-key")).thenReturn("not-json");

        assertTrue(cache.get("cache-key").isEmpty());
        verify(redisTemplate).delete("cache-key");
    }

    @Test
    void containsRedisWriteException() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        JsonNode content = objectMapper.readTree("{\"term\":\"innovative\"}");
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations).set("cache-key", "{\"term\":\"innovative\"}", Duration.ofDays(7));

        assertDoesNotThrow(() -> cache.put("cache-key", content, Duration.ofDays(7)));
    }
}
