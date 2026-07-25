package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyGenerationCache {

    private static final Logger log = LoggerFactory.getLogger(VocabularyGenerationCache.class);
    private static final String KEY_PREFIX = "vocabulary:generation:v2:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public VocabularyGenerationCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String key(String themeUid, int themeVersion, JsonNode core, String sourceContext) {
        if (core == null || !core.isObject()) {
            throw new IllegalArgumentException("Vocabulary generation cache core must be an object");
        }
        ObjectNode material = objectMapper.createObjectNode();
        material.put("themeUid", valueOrEmpty(themeUid));
        material.put("themeVersion", themeVersion);
        material.put("coreHash", sha256(canonicalBytes(core)));
        material.put("sourceContext", valueOrEmpty(sourceContext));
        return KEY_PREFIX + sha256(canonicalBytes(material));
    }

    public Optional<CachedGeneration> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                JsonNode stored = objectMapper.readTree(value);
                JsonNode core = stored == null ? null : stored.get("core");
                JsonNode markdown = stored == null ? null : stored.get("markdown");
                if (stored != null && stored.isObject() && core != null && core.isObject()
                        && markdown != null && markdown.isTextual()) {
                    return Optional.of(new CachedGeneration(core, markdown.textValue()));
                }
                log.warn("Vocabulary generation cache content rejected key={} reasonType=InvalidShape", key);
            } catch (JsonProcessingException exception) {
                log.warn("Vocabulary generation cache content rejected key={} reasonType={}",
                        key, exception.getClass().getSimpleName());
            }
            evict(key);
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Vocabulary generation cache read failed key={} reasonType={}",
                    key, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(String key, CachedGeneration value, Duration ttl) {
        if (value == null || value.core() == null || !value.core().isObject() || value.markdown() == null) {
            throw new IllegalArgumentException("Vocabulary generation cache value is invalid");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Vocabulary generation cache TTL must be positive");
        }
        ObjectNode stored = objectMapper.createObjectNode();
        stored.set("core", value.core());
        stored.put("markdown", value.markdown());
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(stored), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Vocabulary generation cache write failed key={} reasonType={}",
                    key, exception.getClass().getSimpleName());
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Vocabulary generation cache eviction failed key={} reasonType={}",
                    key, exception.getClass().getSimpleName());
        }
    }

    private byte[] canonicalBytes(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize vocabulary generation cache material");
        }
    }

    private String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                hex.append(String.format("%02x", part & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available");
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public record CachedGeneration(JsonNode core, String markdown) {}
}
