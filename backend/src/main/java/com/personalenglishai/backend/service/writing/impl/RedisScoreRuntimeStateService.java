package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisScoreRuntimeStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisScoreRuntimeStateService.class);
    private static final String KEY_PREFIX = "peai:score:runtime:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisScoreRuntimeStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ScoreRuntimeState get(String docId) {
        String normalizedDocId = trimToNull(docId);
        if (normalizedDocId == null) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key(normalizedDocId));
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, ScoreRuntimeState.class);
        } catch (Exception e) {
            log.warn("score runtime state read failed docId={} error={}", normalizedDocId, e.getMessage());
            return null;
        }
    }

    public void save(String docId, ScoreRuntimeState state) {
        String normalizedDocId = trimToNull(docId);
        if (normalizedDocId == null || state == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(normalizedDocId), objectMapper.writeValueAsString(state), TTL);
        } catch (Exception e) {
            log.warn("score runtime state save failed docId={} error={}", normalizedDocId, e.getMessage());
        }
    }

    public void clear(String docId) {
        String normalizedDocId = trimToNull(docId);
        if (normalizedDocId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(normalizedDocId));
        } catch (Exception e) {
            log.warn("score runtime state clear failed docId={} error={}", normalizedDocId, e.getMessage());
        }
    }

    private String key(String docId) {
        return KEY_PREFIX + docId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
