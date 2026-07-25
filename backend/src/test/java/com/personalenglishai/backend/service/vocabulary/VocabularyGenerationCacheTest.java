package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    void keyHashesThemeIdentityVersionCoreAndSourceContext() throws Exception {
        ObjectNode core = core("record", "a written account");

        String base = cache.key("theme-1", 3, core, "captured sentence one");

        assertTrue(base.matches("vocabulary:generation:v2:[0-9a-f]{64}"));
        assertNotEquals(base, cache.key("theme-2", 3, core, "captured sentence one"));
        assertNotEquals(base, cache.key("theme-1", 4, core, "captured sentence one"));
        assertNotEquals(base, cache.key("theme-1", 3,
                core("record", "stored information"), "captured sentence one"));
        assertNotEquals(base, cache.key("theme-1", 3, core, "captured sentence two"));
    }

    @Test
    void storesAndReadsCoreAndMarkdownWithRequestedTtl() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectNode core = core("record", "a written account");
        VocabularyGenerationCache.CachedGeneration value =
                new VocabularyGenerationCache.CachedGeneration(core, "## Usage");
        ObjectNode serialized = objectMapper.createObjectNode();
        serialized.set("core", core);
        serialized.put("markdown", "## Usage");

        cache.put("cache-key", value, Duration.ofDays(7));

        verify(valueOperations).set(
                "cache-key", objectMapper.writeValueAsString(serialized), Duration.ofDays(7));
    }

    @Test
    void deserializesStoredCoreAndMarkdown() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache-key")).thenReturn(
                "{\"core\":{\"term\":\"record\"},\"markdown\":\"## Cached\"}");

        VocabularyGenerationCache.CachedGeneration value = cache.get("cache-key").orElseThrow();

        assertEquals("record", value.core().path("term").asText());
        assertEquals("## Cached", value.markdown());
    }

    @Test
    void evictsMalformedOrIncompleteSerializedContent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache-key")).thenReturn("{\"core\":{}}");

        assertTrue(cache.get("cache-key").isEmpty());
        verify(redisTemplate).delete("cache-key");
    }

    @Test
    void containsRedisFailures() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("read-key")).thenThrow(new IllegalStateException("redis unavailable"));
        ObjectNode core = core("record", "a written account");
        String serialized = "{\"core\":" + core + ",\"markdown\":\"## Usage\"}";
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations).set("write-key", serialized, Duration.ofDays(7));

        assertTrue(cache.get("read-key").isEmpty());
        assertDoesNotThrow(() -> cache.put("write-key",
                new VocabularyGenerationCache.CachedGeneration(core, "## Usage"), Duration.ofDays(7)));
    }

    private ObjectNode core(String term, String definition) throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {"schemaVersion":1,"term":"%s","phonetics":[],"senses":[
                  {"partOfSpeech":"noun","meanings":[
                    {"definitionEn":"%s","definitionZh":""}]}]}
                """.formatted(term, definition));
    }
}
