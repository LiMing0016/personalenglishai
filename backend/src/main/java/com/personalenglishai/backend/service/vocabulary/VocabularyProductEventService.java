package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyProductEventBatchRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyProductEventBatchResponse;
import com.personalenglishai.backend.entity.vocabulary.VocabularyProductEvent;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyProductEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class VocabularyProductEventService {
    private static final int MAX_EVENTS = 50;
    private static final int MAX_PROPERTY_STRING_LENGTH = 256;
    private static final int MAX_ARRAY_LENGTH = 20;
    private static final int MAX_ARRAY_STRING_LENGTH = 128;
    private static final BigDecimal MAX_ABSOLUTE_NUMBER = new BigDecimal("1000000000000000");
    private static final Set<String> EVENT_NAMES = Set.of(
            "vocabulary_image_recognition_started",
            "vocabulary_image_recognition_completed",
            "vocabulary_image_candidates_confirmed",
            "vocabulary_capture_submitted",
            "vocabulary_cards_ready",
            "vocabulary_learning_started");
    private static final Set<String> PROPERTY_KEYS = Set.of(
            "sourceType", "durationMs", "candidateCount", "suspectedCount", "selectedCount",
            "editedCount", "removedCount", "resolutionCount", "successCount", "failedCount",
            "provider", "model", "promptVersion", "modelCallCount", "warningCodes", "outcome");
    private static final Set<String> FORBIDDEN_PROPERTY_KEYS = Set.of(
            "filename", "term", "observedtext", "contexttext", "rawtext", "content",
            "markdown", "image", "base64");

    private final VocabularyProductEventMapper mapper;
    private final ObjectMapper objectMapper;

    public VocabularyProductEventService(VocabularyProductEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VocabularyProductEventBatchResponse acceptBatch(
            Long userId, VocabularyProductEventBatchRequest request) {
        List<VocabularyProductEvent> events = validateAndMap(userId, request);
        int accepted = 0;
        for (VocabularyProductEvent event : events) {
            accepted += mapper.insertIgnore(event) == 1 ? 1 : 0;
        }
        return new VocabularyProductEventBatchResponse(accepted, events.size() - accepted);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordServerEvent(Long userId, ServerEvent event) {
        if (event == null) throw invalid("Server event is required");
        VocabularyProductEvent mapped = mapEvent(userId, new VocabularyProductEventBatchRequest.Event(
                event.eventUid(),
                event.eventName(),
                event.traceId(),
                "server",
                event.cardUid(),
                LocalDateTime.now(ZoneOffset.UTC),
                event.properties()));
        return mapper.insertIgnore(mapped) == 1;
    }

    public record ServerEvent(
            String eventUid,
            String eventName,
            String traceId,
            String cardUid,
            Map<String, Object> properties) {
    }

    private List<VocabularyProductEvent> validateAndMap(
            Long userId, VocabularyProductEventBatchRequest request) {
        if (userId == null) throw invalid("Authenticated user is required");
        if (request == null || request.events() == null
                || request.events().isEmpty() || request.events().size() > MAX_EVENTS) {
            throw invalid("Event batch must contain 1 to 50 events");
        }
        List<VocabularyProductEvent> mapped = new ArrayList<>(request.events().size());
        for (VocabularyProductEventBatchRequest.Event event : request.events()) {
            mapped.add(mapEvent(userId, event));
        }
        return mapped;
    }

    private VocabularyProductEvent mapEvent(Long userId, VocabularyProductEventBatchRequest.Event event) {
        if (event == null) throw invalid("Event is required");
        requireText(event.eventUid(), 128, "Invalid event identity");
        requireText(event.eventName(), 64, "Invalid event name");
        if (!EVENT_NAMES.contains(event.eventName())) throw invalid("Unsupported event name");
        requireOptionalText(event.traceId(), 128, "Invalid trace identity");
        requireText(event.sessionId(), 128, "Invalid session identity");
        requireOptionalText(event.cardUid(), 64, "Invalid card identity");
        if (event.occurredAt() == null) throw invalid("Event time is required");

        VocabularyProductEvent mapped = new VocabularyProductEvent();
        mapped.setEventUid(event.eventUid());
        mapped.setUserId(userId);
        mapped.setEventName(event.eventName());
        mapped.setTraceId(blankToNull(event.traceId()));
        mapped.setSessionId(event.sessionId());
        mapped.setCardUid(blankToNull(event.cardUid()));
        mapped.setOccurredAt(event.occurredAt());
        mapped.setPropertiesJson(serializeProperties(event.properties()));
        return mapped;
    }

    private String serializeProperties(Map<String, Object> properties) {
        TreeMap<String, Object> safe = new TreeMap<>();
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                if (key == null || FORBIDDEN_PROPERTY_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                    throw invalid("Sensitive event property is not allowed");
                }
                if (!PROPERTY_KEYS.contains(key)) throw invalid("Unsupported event property");
                safe.put(key, validatePropertyValue(entry.getValue()));
            }
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException exception) {
            throw invalid("Event properties are not serializable");
        }
    }

    private Object validatePropertyValue(Object value) {
        if (value instanceof String text) {
            if (text.length() > MAX_PROPERTY_STRING_LENGTH) throw invalid("Event property is too long");
            return text;
        }
        if (value instanceof Boolean) return value;
        if (value instanceof Number number) return validateNumber(number);
        if (value instanceof List<?> list) {
            if (list.size() > MAX_ARRAY_LENGTH) throw invalid("Event property array is too long");
            List<Object> safe = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof String text) {
                    if (text.length() > MAX_ARRAY_STRING_LENGTH) {
                        throw invalid("Event property array item is too long");
                    }
                    safe.add(text);
                } else if (item instanceof Boolean) {
                    safe.add(item);
                } else if (item instanceof Number number) {
                    safe.add(validateNumber(number));
                } else {
                    throw invalid("Event property arrays must contain scalar values");
                }
            }
            return List.copyOf(safe);
        }
        throw invalid("Event property must be a bounded scalar or short array");
    }

    private Number validateNumber(Number number) {
        if (number instanceof Double value && !Double.isFinite(value)) {
            throw invalid("Event property number must be finite");
        }
        if (number instanceof Float value && !Float.isFinite(value)) {
            throw invalid("Event property number must be finite");
        }
        BigDecimal decimal;
        try {
            decimal = number instanceof BigDecimal value ? value
                    : number instanceof BigInteger value ? new BigDecimal(value)
                    : new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw invalid("Event property number is invalid");
        }
        if (decimal.abs().compareTo(MAX_ABSOLUTE_NUMBER) > 0) {
            throw invalid("Event property number is out of range");
        }
        return number;
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.length() > maxLength) throw invalid(message);
    }

    private void requireOptionalText(String value, int maxLength, String message) {
        if (value != null && (!value.isBlank() && value.length() > maxLength)) throw invalid(message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
