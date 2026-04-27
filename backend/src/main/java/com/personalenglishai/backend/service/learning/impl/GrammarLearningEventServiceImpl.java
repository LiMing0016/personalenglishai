package com.personalenglishai.backend.service.learning.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchRequest;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchResult;
import com.personalenglishai.backend.entity.GrammarLearningEvent;
import com.personalenglishai.backend.mapper.GrammarLearningEventMapper;
import com.personalenglishai.backend.service.learning.GrammarLearningEventService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GrammarLearningEventServiceImpl implements GrammarLearningEventService {
    private final GrammarLearningEventMapper mapper;
    private final ObjectMapper objectMapper;

    public GrammarLearningEventServiceImpl(GrammarLearningEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public GrammarLearningEventBatchResult acceptBatch(Long authenticatedUserId, GrammarLearningEventBatchRequest request) {
        Long bodyUserId = request.getUserId();
        if (bodyUserId != null && !bodyUserId.equals(authenticatedUserId)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "request userId does not match authenticated user");
        }

        int accepted = 0;
        int deduplicated = 0;
        int rejected = 0;
        List<GrammarLearningEventBatchResult.EventResult> results = new ArrayList<>();
        for (GrammarLearningEventBatchRequest.Event eventRequest : request.getEvents()) {
            try {
                GrammarLearningEvent event = toEntity(authenticatedUserId, request, eventRequest);
                int inserted = mapper.insertIgnore(event);
                if (inserted > 0) {
                    accepted++;
                    results.add(new GrammarLearningEventBatchResult.EventResult(event.getEventId(), "accepted"));
                } else {
                    deduplicated++;
                    results.add(new GrammarLearningEventBatchResult.EventResult(event.getEventId(), "deduplicated"));
                }
            } catch (IllegalArgumentException ex) {
                rejected++;
                results.add(new GrammarLearningEventBatchResult.EventResult(eventRequest.getEventId(), "rejected"));
            }
        }
        return new GrammarLearningEventBatchResult(true, accepted, deduplicated, rejected, results);
    }

    private GrammarLearningEvent toEntity(
            Long userId,
            GrammarLearningEventBatchRequest request,
            GrammarLearningEventBatchRequest.Event eventRequest
    ) {
        GrammarLearningEvent event = new GrammarLearningEvent();
        event.setEventId(eventRequest.getEventId());
        event.setUserId(userId);
        event.setConversationId(firstNonBlank(eventRequest.getPayload(), "conversationId", request.getConversationId()));
        event.setMessageId(firstNonBlank(eventRequest.getPayload(), "messageId", request.getMessageId()));
        event.setEventType(eventRequest.getEventType());
        event.setOccurredAt(parseOccurredAt(eventRequest.getOccurredAt()));
        event.setStudyStage(eventRequest.getStudyStage());
        event.setAssistantMode(eventRequest.getAssistantMode());
        event.setSourceAgent(eventRequest.getSourceAgent());
        event.setTaskType(eventRequest.getTaskType());
        event.setContentOrigin(StringUtils.hasText(eventRequest.getContentOrigin()) ? eventRequest.getContentOrigin() : "user_input");
        event.setProfileEligible(eventRequest.getProfileEligible() == null || eventRequest.getProfileEligible());
        event.setConfidence(eventRequest.getConfidence());
        event.setSchemaVersion(eventRequest.getSchemaVersion());
        event.setSkillVersion(eventRequest.getSkillVersion());
        event.setTaxonomyVersion(eventRequest.getTaxonomyVersion());
        event.setPromptVersion(eventRequest.getPromptVersion());
        event.setModelVersion(eventRequest.getModelVersion());
        event.setGrammarQuestionType(firstNonBlank(eventRequest.getGrammarQuestionType(), stringPayload(eventRequest.getPayload(), "grammarQuestionType")));
        event.setGrammarErrorType(firstNonBlank(eventRequest.getGrammarErrorType(), stringPayload(eventRequest.getPayload(), "grammarErrorType")));
        event.setStyleIssueType(firstNonBlank(eventRequest.getStyleIssueType(), stringPayload(eventRequest.getPayload(), "styleIssueType")));
        event.setSeverity(firstNonBlank(eventRequest.getSeverity(), stringPayload(eventRequest.getPayload(), "severity")));
        event.setSentenceHash(firstNonBlank(eventRequest.getSentenceHash(), stringPayload(eventRequest.getPayload(), "sentenceHash")));
        event.setPayloadJson(toPayloadJson(eventRequest.getPayload()));
        return event;
    }

    private java.time.LocalDateTime parseOccurredAt(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid occurredAt", ex);
        }
    }

    private String toPayloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid payload", ex);
        }
    }

    private String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }

    private String firstNonBlank(Map<String, Object> payload, String key, String fallback) {
        return firstNonBlank(stringPayload(payload, key), fallback);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
