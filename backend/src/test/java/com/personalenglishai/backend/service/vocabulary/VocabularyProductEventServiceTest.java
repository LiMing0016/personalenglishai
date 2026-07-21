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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Mock VocabularyProductEventMapper mapper;
    private ObjectMapper objectMapper;
    private VocabularyProductEventService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new VocabularyProductEventService(mapper, objectMapper);
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
        properties.put("warningCodes", List.of("LOW_CONFIDENCE"));
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
        assertEquals(List.of("LOW_CONFIDENCE"), decoded.get("warningCodes"));
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
                "vocabulary-cards-ready:rev_1",
                "vocabulary_cards_ready",
                "trace-safe",
                "card_1",
                Map.of("sourceType", "ocr_image"));

        assertTrue(service.recordServerEvent(7L, serverEvent));

        Method writer = VocabularyProductEventService.class.getMethod(
                "recordServerEvent", Long.class, VocabularyProductEventService.ServerEvent.class);
        Transactional transaction = writer.getAnnotation(Transactional.class);
        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());

        var forbidden = new VocabularyProductEventService.ServerEvent(
                "vocabulary-cards-ready:rev_2",
                "vocabulary_cards_ready",
                "trace-safe",
                "card_1",
                Map.of("RawText", "private"));
        assertThrows(IllegalArgumentException.class, () -> service.recordServerEvent(7L, forbidden));
    }

    private VocabularyProductEventBatchRequest.Event event(
            String eventUid, String eventName, Map<String, Object> properties) {
        return new VocabularyProductEventBatchRequest.Event(
                eventUid,
                eventName,
                "trace-safe",
                "session-safe",
                "card_safe",
                NOW,
                properties);
    }
}
