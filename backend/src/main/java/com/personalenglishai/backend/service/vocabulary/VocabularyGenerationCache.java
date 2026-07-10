package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
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
    private static final String KEY_PREFIX = "vocabulary:generation:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public VocabularyGenerationCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String key(
            String normalizedTerm,
            String templateKey,
            int templateVersion,
            DictionaryLookupResponse dictionaryData,
            String capturedContext) {
        ObjectNode material = objectMapper.createObjectNode();
        material.put("normalizedTerm", valueOrEmpty(normalizedTerm));
        material.put("templateKey", valueOrEmpty(templateKey));
        material.put("templateVersion", templateVersion);
        material.set("dictionary", dictionaryContent(dictionaryData));
        material.put("capturedContext", valueOrEmpty(capturedContext));
        try {
            return KEY_PREFIX + sha256(objectMapper.writeValueAsBytes(material));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build vocabulary generation cache key");
        }
    }

    public Optional<JsonNode> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            try {
                JsonNode content = objectMapper.readTree(value);
                if (content != null && content.isObject()) {
                    return Optional.of(content);
                }
                log.warn("Vocabulary generation cache content rejected key={} reasonType=NonObjectJson", key);
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

    public void put(String key, JsonNode content, Duration ttl) {
        if (content == null || !content.isObject()) {
            throw new IllegalArgumentException("Vocabulary generation cache content must be an object");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Vocabulary generation cache TTL must be positive");
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(content), ttl);
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

    private ObjectNode dictionaryContent(DictionaryLookupResponse response) {
        ObjectNode content = objectMapper.createObjectNode();
        if (response == null) {
            return content;
        }
        content.put("word", valueOrEmpty(response.getWord()));
        content.put("language", valueOrEmpty(response.getLanguage()));
        content.put("source", valueOrEmpty(response.getSource()));

        ArrayNode phonetics = content.putArray("phonetics");
        for (DictionaryPhoneticDto phonetic : response.getPhonetics()) {
            if (phonetic == null) {
                continue;
            }
            ObjectNode item = phonetics.addObject();
            item.put("text", valueOrEmpty(phonetic.getText()));
            item.put("audioUrl", valueOrEmpty(phonetic.getAudioUrl()));
        }

        ArrayNode entries = content.putArray("entries");
        for (DictionaryEntryDto entry : response.getEntries()) {
            if (entry == null) {
                continue;
            }
            ObjectNode item = entries.addObject();
            item.put("partOfSpeech", valueOrEmpty(entry.getPartOfSpeech()));
            addStrings(item.putArray("definitions"), entry.getDefinitions());
            addStrings(item.putArray("examples"), entry.getExamples());
        }
        return content;
    }

    private void addStrings(ArrayNode target, Iterable<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            target.add(valueOrEmpty(value));
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
}
