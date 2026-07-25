package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyProductEventBatchRequest;
import com.personalenglishai.backend.entity.vocabulary.VocabularyProductEvent;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyProductEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyProductEventServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 4, 30);
    private static final String HEX_32 = "0123456789abcdef0123456789abcdef";
    private static final String HEX_64 = HEX_32 + HEX_32;
    private static final String TRACE_ID = "vocab-image-" + HEX_32;
    private static final String SESSION_ID = "vocabulary-session:" + HEX_32;
    private static final String CARD_UID = "card_" + HEX_32;
    private static final String REVISION_UID = "rev_" + HEX_32;
    private static final String ALLOWED_MODEL = "openai/gpt-4.1-mini";
    private static final String ALLOWED_NAMESPACED_MODEL = "gateway/qwen/qwen2.5-vl-72b-instruct";

    @Mock VocabularyProductEventMapper mapper;
    private ObjectMapper objectMapper;
    private VocabularyProductEventService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new VocabularyProductEventService(
                mapper, objectMapper, Set.of(ALLOWED_MODEL, ALLOWED_NAMESPACED_MODEL));
    }

    @Test
    void acceptsNewEventsAndCountsDuplicateEventUidsWithoutReinsertingData() {
        when(mapper.insertIgnore(any())).thenReturn(1, 0);

        var response = service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(
                event("event-1", "vocabulary_image_recognition_started", Map.of("sourceType", "ocr_image")),
                event("event-1", "vocabulary_image_recognition_started", Map.of("sourceType", "ocr_image")))));

        assertEquals(1, response.accepted());
        assertEquals(1, response.duplicate());
    }

    @Test
    void rejectsUnknownEventNamesBeforePersistence() {
        var request = new VocabularyProductEventBatchRequest(List.of(
                event("event-1", "vocabulary_private_payload", Map.of())));

        assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, request));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsMoreThanFiftyEventsBeforePersistence() {
        List<VocabularyProductEventBatchRequest.Event> events = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            events.add(event("event-" + index, "vocabulary_capture_submitted", Map.of()));
        }

        assertThrows(IllegalArgumentException.class,
                () -> service.acceptBatch(7L, new VocabularyProductEventBatchRequest(events)));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsForbiddenPropertyKeysWithoutCaseSensitiveBypass() {
        for (String key : List.of(
                "fileName", "TERM", "ObservedText", "contexttext", "rawText",
                "CONTENT", "Markdown", "IMAGE", "Base64")) {
            var request = new VocabularyProductEventBatchRequest(List.of(
                    event("event-" + key, "vocabulary_image_recognition_completed", Map.of(key, "secret"))));

            assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, request));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsUnknownPropertyKeysIncludingAllowedKeyCaseVariants() {
        for (String key : List.of("source_type", "SourceType", "extra")) {
            var request = new VocabularyProductEventBatchRequest(List.of(
                    event("event-" + key, "vocabulary_capture_submitted", Map.of(key, "manual"))));

            assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, request));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsNestedObjectsComplexCollectionsAndNonFiniteNumbers() {
        List<Object> invalidValues = List.of(
                Map.of("nested", true),
                Set.of("one", "two"),
                List.of(List.of("nested")),
                Double.NaN,
                Double.POSITIVE_INFINITY);

        for (int index = 0; index < invalidValues.size(); index++) {
            var request = new VocabularyProductEventBatchRequest(List.of(event(
                    "event-" + index,
                    "vocabulary_image_recognition_completed",
                    Map.of("outcome", invalidValues.get(index)))));
            assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, request));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsPropertyTypeConfusionAndUnsupportedEnums() {
        List<Map<String, Object>> invalidProperties = List.of(
                Map.of("candidateCount", "原词"),
                Map.of("durationMs", true),
                Map.of("outcome", List.of("raw text")),
                Map.of("model", "文件 名/内容"),
                Map.of("model", "receive"),
                Map.of("model", "private.png"),
                Map.of("model", "receive/private.png"),
                Map.of("model", "private/gpt-4o"),
                Map.of("model", "test/receive"),
                Map.of("provider", "receive"),
                Map.of("promptVersion", "vocabulary-image-recognition-v2"),
                Map.of("sourceType", "clipboard"),
                Map.of("warningCodes", List.of("LOW_CONFIDENCE")),
                Map.of("candidateCount", -1),
                Map.of("modelCallCount", 1.5));

        for (int index = 0; index < invalidProperties.size(); index++) {
            var request = new VocabularyProductEventBatchRequest(List.of(event(
                    "event-invalid-" + index,
                    "vocabulary_image_recognition_completed",
                    invalidProperties.get(index))));

            assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, request));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsPropertiesThatDoNotBelongToTheEvent() {
        List<VocabularyProductEventBatchRequest.Event> invalidEvents = List.of(
                event("event-mismatch-1", "vocabulary_image_recognition_started",
                        Map.of("outcome", "success")),
                event("event-mismatch-2", "vocabulary_capture_submitted",
                        Map.of("candidateCount", 1)),
                event("event-mismatch-3", "vocabulary_learning_started",
                        Map.of("provider", "openai")));

        for (VocabularyProductEventBatchRequest.Event event : invalidEvents) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(event))));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsContentLikeAndWronglyPrefixedIdentifiers() {
        List<String> contentLike = List.of("receive", "private.png", "abc123", "wrong:" + HEX_32);
        List<VocabularyProductEventBatchRequest.Event> invalidEvents = new ArrayList<>();
        for (String invalid : contentLike) {
            invalidEvents.add(new VocabularyProductEventBatchRequest.Event(
                    invalid, "vocabulary_image_recognition_started", TRACE_ID,
                    SESSION_ID, CARD_UID, NOW, Map.of("sourceType", "ocr_image")));
            invalidEvents.add(new VocabularyProductEventBatchRequest.Event(
                    browserEventUid("trace-" + invalid), "vocabulary_image_recognition_started", invalid,
                    SESSION_ID, CARD_UID, NOW, Map.of("sourceType", "ocr_image")));
            invalidEvents.add(new VocabularyProductEventBatchRequest.Event(
                    browserEventUid("session-" + invalid), "vocabulary_image_recognition_started", TRACE_ID,
                    invalid, CARD_UID, NOW, Map.of("sourceType", "ocr_image")));
            invalidEvents.add(new VocabularyProductEventBatchRequest.Event(
                    browserEventUid("card-" + invalid), "vocabulary_image_recognition_started", TRACE_ID,
                    SESSION_ID, invalid, NOW, Map.of("sourceType", "ocr_image")));
        }

        for (VocabularyProductEventBatchRequest.Event event : invalidEvents) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(event))));
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void acceptsOnlyProductionIdentifierForms() {
        when(mapper.insertIgnore(any())).thenReturn(1);
        List<VocabularyProductEventBatchRequest.Event> events = List.of(
                new VocabularyProductEventBatchRequest.Event(
                        "vocabulary-event:" + HEX_32,
                        "vocabulary_learning_started", TRACE_ID, SESSION_ID, CARD_UID, NOW,
                        Map.of("sourceType", "ocr_image")),
                new VocabularyProductEventBatchRequest.Event(
                        "vocabulary-event:123e4567-e89b-12d3-a456-426614174000",
                        "vocabulary_learning_started", "capture:" + HEX_64,
                        "vocabulary-session:123e4567-e89b-12d3-a456-426614174000",
                        CARD_UID, NOW, Map.of("sourceType", "manual")),
                new VocabularyProductEventBatchRequest.Event(
                        "vocabulary-capture-submitted:" + HEX_64,
                        "vocabulary_capture_submitted", "capture:" + HEX_64, "server", null, NOW,
                        Map.of("sourceType", "manual", "successCount", 1, "failedCount", 0)),
                new VocabularyProductEventBatchRequest.Event(
                        "vocabulary-cards-ready:" + REVISION_UID,
                        "vocabulary_cards_ready", TRACE_ID, "server", CARD_UID, NOW,
                        Map.of("sourceType", "ocr_image")));

        for (VocabularyProductEventBatchRequest.Event event : events) {
            service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(event)));
        }
    }

    @Test
    void rejectsOverlongStringsAndArrays() {
        var longString = new VocabularyProductEventBatchRequest(List.of(event(
                "event-long", "vocabulary_image_recognition_completed",
                Map.of("model", "x".repeat(257)))));
        var longArray = new VocabularyProductEventBatchRequest(List.of(event(
                "event-array", "vocabulary_image_recognition_completed",
                Map.of("warningCodes", java.util.Collections.nCopies(21, "WARNING")))));

        assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, longString));
        assertThrows(IllegalArgumentException.class, () -> service.acceptBatch(7L, longArray));
        verifyNoInteractions(mapper);
    }

    @Test
    void serializesAllowedPropertiesInStableKeyOrderWithoutSensitiveValues() throws Exception {
        when(mapper.insertIgnore(any())).thenReturn(1);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("warningCodes", List.of(
                "CANDIDATE_LIMIT_REACHED",
                "DICTIONARY_VERIFICATION_UNAVAILABLE",
                "CANDIDATE_LIMIT_REACHED"));
        properties.put("candidateCount", 3);
        properties.put("sourceType", "ocr_image");
        properties.put("durationMs", 125L);
        properties.put("outcome", "success");

        service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(event(
                "event-safe", "vocabulary_image_recognition_completed", properties))));

        ArgumentCaptor<VocabularyProductEvent> captor = ArgumentCaptor.forClass(VocabularyProductEvent.class);
        verify(mapper).insertIgnore(captor.capture());
        String json = captor.getValue().getPropertiesJson();
        assertTrue(json.indexOf("candidateCount") < json.indexOf("durationMs"));
        assertTrue(json.indexOf("durationMs") < json.indexOf("outcome"));
        Map<String, Object> decoded = objectMapper.readValue(json, new TypeReference<>() {});
        assertEquals(List.of("candidateCount", "durationMs", "outcome", "sourceType", "warningCodes"),
                new ArrayList<>(decoded.keySet()));
        assertEquals(3, decoded.get("candidateCount"));
        assertEquals(125, decoded.get("durationMs"));
        assertEquals("success", decoded.get("outcome"));
        assertEquals("ocr_image", decoded.get("sourceType"));
        assertEquals(List.of(
                "CANDIDATE_LIMIT_REACHED",
                "DICTIONARY_VERIFICATION_UNAVAILABLE"), decoded.get("warningCodes"));
    }

    @Test
    void acceptsEveryProductionEventPayload() {
        when(mapper.insertIgnore(any())).thenReturn(1);

        List<VocabularyProductEventBatchRequest.Event> events = List.of(
                event("event-prod-1", "vocabulary_image_recognition_started",
                        Map.of("sourceType", "ocr_image")),
                event("event-prod-2", "vocabulary_image_recognition_completed", Map.of(
                        "sourceType", "ocr_image", "durationMs", 250, "candidateCount", 3,
                        "suspectedCount", 1, "provider", "openai", "model", ALLOWED_MODEL,
                        "promptVersion", "vocabulary-image-recognition-v1", "modelCallCount", 1,
                        "warningCodes", List.of("CANDIDATE_LIMIT_REACHED"), "outcome", "success")),
                event("event-prod-3", "vocabulary_image_candidates_confirmed", Map.of(
                        "sourceType", "ocr_image", "candidateCount", 3, "suspectedCount", 1,
                        "selectedCount", 2, "editedCount", 1, "removedCount", 1,
                        "resolutionCount", 1)),
                event("event-prod-4", "vocabulary_capture_submitted",
                        Map.of("sourceType", "manual", "successCount", 2, "failedCount", 0)),
                event("event-prod-5", "vocabulary_cards_ready",
                        Map.of("sourceType", "dictionary")),
                event("event-prod-6", "vocabulary_learning_started",
                        Map.of("sourceType", "ocr_image")));

        for (VocabularyProductEventBatchRequest.Event event : events) {
            service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(event)));
        }
    }

    @Test
    void acceptsOnlyExactlyConfiguredImageModelsIncludingNamespacedValues() {
        when(mapper.insertIgnore(any())).thenReturn(1);
        List<String> models = List.of(ALLOWED_MODEL, ALLOWED_NAMESPACED_MODEL);

        for (String model : models) {
            service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(event(
                    "model-" + model,
                    "vocabulary_image_recognition_completed",
                    Map.of("provider", "openai", "model", model,
                            "promptVersion", "vocabulary-image-recognition-v1")))));
        }
    }

    @Test
    void rejectsModelWhenConfiguredAllowlistIsEmptyOrValueIsNotAnExactMatch() {
        VocabularyProductEventService emptyAllowlist =
                new VocabularyProductEventService(mapper, objectMapper, Set.of());
        VocabularyProductEventService exactAllowlist =
                new VocabularyProductEventService(mapper, objectMapper, Set.of("openai/gpt-4o"));

        var configuredModel = new VocabularyProductEventBatchRequest(List.of(event(
                "empty-model", "vocabulary_image_recognition_completed",
                Map.of("model", "openai/gpt-4o"))));
        var familyOnlyVariant = new VocabularyProductEventBatchRequest(List.of(event(
                "variant-model", "vocabulary_image_recognition_completed",
                Map.of("model", "gpt-4o"))));

        assertThrows(IllegalArgumentException.class,
                () -> emptyAllowlist.acceptBatch(7L, configuredModel));
        assertThrows(IllegalArgumentException.class,
                () -> exactAllowlist.acceptBatch(7L, familyOnlyVariant));
        verifyNoInteractions(mapper);
    }

    @Test
    void acceptsOnlyTheDocumentedEventNames() {
        when(mapper.insertIgnore(any())).thenReturn(1);
        for (String eventName : List.of(
                "vocabulary_image_recognition_started",
                "vocabulary_image_recognition_completed",
                "vocabulary_image_candidates_confirmed",
                "vocabulary_capture_submitted",
                "vocabulary_cards_ready",
                "vocabulary_learning_started")) {
            service.acceptBatch(7L, new VocabularyProductEventBatchRequest(List.of(
                    event("event-" + eventName, eventName, Map.of()))));
        }
    }

    @Test
    void serverEventWriterUsesRequiresNewAndTheSamePrivacyValidation() throws Exception {
        when(mapper.insertIgnore(any())).thenReturn(1);
        var serverEvent = new VocabularyProductEventService.ServerEvent(
                "vocabulary-cards-ready:" + REVISION_UID,
                "vocabulary_cards_ready",
                TRACE_ID,
                CARD_UID,
                Map.of("sourceType", "ocr_image"));

        assertTrue(service.recordServerEvent(7L, serverEvent));

        Method writer = VocabularyProductEventService.class.getMethod(
                "recordServerEvent", Long.class, VocabularyProductEventService.ServerEvent.class);
        Transactional transaction = writer.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());

        var forbidden = new VocabularyProductEventService.ServerEvent(
                "vocabulary-cards-ready:" + REVISION_UID,
                "vocabulary_cards_ready",
                TRACE_ID,
                CARD_UID,
                Map.of("RawText", "private"));
        assertThrows(IllegalArgumentException.class, () -> service.recordServerEvent(7L, forbidden));
    }

    private VocabularyProductEventBatchRequest.Event event(
            String eventUid, String eventName, Map<String, Object> properties) {
        return new VocabularyProductEventBatchRequest.Event(
                browserEventUid(eventUid),
                eventName,
                TRACE_ID,
                SESSION_ID,
                CARD_UID,
                NOW,
                properties);
    }

    private String browserEventUid(String seed) {
        UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return "vocabulary-event:" + uuid;
    }
}
