package com.personalenglishai.backend.ai.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisAssistantConversationStateService implements AssistantConversationStateService {

    private static final Logger log = LoggerFactory.getLogger(RedisAssistantConversationStateService.class);
    private static final String KEY_PREFIX = "peai:assistant:response:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public RedisAssistantConversationStateService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String getLastResponseId(String conversationId) {
        if (isBlank(conversationId)) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key(conversationId));
        } catch (Exception e) {
            log.warn("assistant conversation state read failed conversationId={} error={}", conversationId, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveLastResponseId(String conversationId, String responseId) {
        if (isBlank(conversationId) || isBlank(responseId)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(conversationId), responseId, TTL);
        } catch (Exception e) {
            log.warn("assistant conversation state save failed conversationId={} error={}", conversationId, e.getMessage());
        }
    }

    @Override
    public void clear(String conversationId) {
        if (isBlank(conversationId)) {
            return;
        }
        try {
            redisTemplate.delete(key(conversationId));
        } catch (Exception e) {
            log.warn("assistant conversation state clear failed conversationId={} error={}", conversationId, e.getMessage());
        }
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
