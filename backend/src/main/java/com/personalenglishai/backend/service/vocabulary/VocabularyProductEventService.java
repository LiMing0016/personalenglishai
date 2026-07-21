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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class VocabularyProductEventService {
    private static final int MAX_EVENTS = 50;
    private static final long MAX_COUNT = 1_000_000L;
    private static final long MAX_DURATION_MS = 86_400_000L;
    private static final long MAX_MODEL_CALL_COUNT = 100L;
    private static final int MAX_WARNING_CODES = 10;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private static final Set<String> EVENT_NAMES = Set.of(
            "vocabulary_image_recognition_started",
            "vocabulary_image_recognition_completed",
            "vocabulary_image_candidates_confirmed",
            "vocabulary_capture_submitted",
            "vocabulary_cards_ready",
            "vocabulary_learning_started");
    private static final Map<String, Set<String>> EVENT_PROPERTY_KEYS = Map.of(
            "vocabulary_image_recognition_started", Set.of("sourceType"),
            "vocabulary_image_recognition_completed", Set.of(
                    "sourceType", "durationMs", "candidateCount", "suspectedCount",
                    "provider", "model", "promptVersion", "modelCallCount", "warningCodes", "outcome"),
            "vocabulary_image_candidates_confirmed", Set.of(
                    "sourceType", "candidateCount", "suspectedCount", "selectedCount",
                    "editedCount", "removedCount", "resolutionCount"),
            "vocabulary_capture_submitted", Set.of("sourceType", "successCount", "failedCount"),
            "vocabulary_cards_ready", Set.of("sourceType"),
            "vocabulary_learning_started", Set.of("sourceType"));
    private static final Set<String> SOURCE_TYPES = Set.of("manual", "dictionary", "ocr_image");
    private static final Set<String> OUTCOMES = Set.of("success", "failed");
    private static final Set<String> WARNING_CODES = Set.of(
            "CANDIDATE_LIMIT_REACHED", "DICTIONARY_VERIFICATION_UNAVAILABLE");
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
        requireIdentifier(event.eventUid(), 128, "Invalid event identity");
        requireText(event.eventName(), 64, "Invalid event name");
        if (!EVENT_NAMES.contains(event.eventName())) throw invalid("Unsupported event name");
        requireOptionalIdentifier(event.traceId(), 128, "Invalid trace identity");
        requireIdentifier(event.sessionId(), 128, "Invalid session identity");
        requireOptionalIdentifier(event.cardUid(), 64, "Invalid card identity");
        if (event.occurredAt() == null) throw invalid("Event time is required");

        VocabularyProductEvent mapped = new VocabularyProductEvent();
        mapped.setEventUid(event.eventUid());
        mapped.setUserId(userId);
        mapped.setEventName(event.eventName());
        mapped.setTraceId(blankToNull(event.traceId()));
        mapped.setSessionId(event.sessionId());
        mapped.setCardUid(blankToNull(event.cardUid()));
        mapped.setOccurredAt(event.occurredAt());
        mapped.setPropertiesJson(serializeProperties(event.eventName(), event.properties()));
        return mapped;
    }

    private String serializeProperties(String eventName, Map<String, Object> properties) {
        TreeMap<String, Object> safe = new TreeMap<>();
        Set<String> allowedKeys = EVENT_PROPERTY_KEYS.get(eventName);
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                if (key == null || FORBIDDEN_PROPERTY_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                    throw invalid("Sensitive event property is not allowed");
                }
                if (allowedKeys == null || !allowedKeys.contains(key)) {
                    throw invalid("Unsupported event property");
                }
                safe.put(key, validatePropertyValue(key, entry.getValue()));
            }
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException exception) {
            throw invalid("Event properties are not serializable");
        }
    }

    private Object validatePropertyValue(String key, Object value) {
        return switch (key) {
            case "durationMs" -> validateNonNegativeInteger(value, MAX_DURATION_MS);
            case "modelCallCount" -> validateNonNegativeInteger(value, MAX_MODEL_CALL_COUNT);
            case "candidateCount", "suspectedCount", "selectedCount", "editedCount",
                    "removedCount", "resolutionCount", "successCount", "failedCount" ->
                    validateNonNegativeInteger(value, MAX_COUNT);
            case "sourceType" -> validateEnum(value, SOURCE_TYPES, "Invalid source type");
            case "outcome" -> validateEnum(value, OUTCOMES, "Invalid event outcome");
            case "provider" -> validateSafePropertyIdentifier(value, 64);
            case "model", "promptVersion" -> validateSafePropertyIdentifier(value, 128);
            case "warningCodes" -> validateWarningCodes(value);
            default -> throw invalid("Unsupported event property");
        };
    }

    private long validateNonNegativeInteger(Object value, long maximum) {
        if (!(value instanceof Number number) || value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw invalid("Event property must be a finite integer");
        }
        BigDecimal decimal;
        try {
            decimal = number instanceof BigDecimal decimalValue ? decimalValue
                    : number instanceof BigInteger integerValue ? new BigDecimal(integerValue)
                    : new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw invalid("Event property number is invalid");
        }
        if (decimal.stripTrailingZeros().scale() > 0
                || decimal.compareTo(BigDecimal.ZERO) < 0
                || decimal.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw invalid("Event property number is out of range");
        }
        return decimal.longValueExact();
    }

    private String validateEnum(Object value, Set<String> allowed, String message) {
        if (!(value instanceof String text) || !allowed.contains(text)) throw invalid(message);
        return text;
    }

    private String validateSafePropertyIdentifier(Object value, int maxLength) {
        if (!(value instanceof String text)) throw invalid("Event property must be a safe identifier");
        requireIdentifier(text, maxLength, "Event property must be a safe identifier");
        return text;
    }

    private List<String> validateWarningCodes(Object value) {
        if (!(value instanceof List<?> list) || list.size() > MAX_WARNING_CODES) {
            throw invalid("Invalid warning codes");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof String code) || !WARNING_CODES.contains(code)) {
                throw invalid("Invalid warning code");
            }
            unique.add(code);
        }
        return List.copyOf(unique);
    }

    private void requireText(String value, int maxLength, String message) {
        if (value == null || value.isBlank() || value.length() > maxLength) throw invalid(message);
    }

    private void requireIdentifier(String value, int maxLength, String message) {
        requireText(value, maxLength, message);
        if (!SAFE_IDENTIFIER.matcher(value).matches()) throw invalid(message);
    }

    private void requireOptionalIdentifier(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) return;
        requireIdentifier(value, maxLength, message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
