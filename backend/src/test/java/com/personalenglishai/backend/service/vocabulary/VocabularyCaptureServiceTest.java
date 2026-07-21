package com.personalenglishai.backend.service.vocabulary;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessResourceFailureException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyCaptureServiceTest {
    @Mock VocabularyCaptureItemService itemService;
    @Mock VocabularyThemeService themeService;
    @Mock VocabularyThemeMapper themeMapper;
    @Mock VocabularyProductEventService productEventService;
    VocabularyCaptureService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyCaptureService(
                itemService, themeService, themeMapper, new VocabularyTermNormalizer(), productEventService);
    }

    @Test
    void bulkCaptureKeepsSuccessfulItemsWhenOneItemHasKnownRejection() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("good", "card_1", "created", "generating", true, "theme_system_basic"));
        when(itemService.captureOne(eq(7L), any(), any(), eq(1)))
                .thenThrow(new VocabularyCaptureRejectedException("invalid term"));

        var result = service.capture(7L,
                VocabularyCaptureRequest.manual("req-bulk", List.of("good", "bad"), "en", "basic"));

        assertEquals(List.of("created", "rejected"),
                result.items().stream().map(VocabularyCaptureResponse.Item::action).toList());
        verify(themeMapper).recordRecentUse(7L, "theme_system_basic");
    }

    @Test
    void resolvesCustomThemeOnceForTheWholeCaptureBatch() {
        ResolvedVocabularyTheme theme = theme("theme_user_1", 3, "basic");
        when(themeService.resolve(7L, "theme_user_1", null)).thenReturn(theme);
        when(itemService.captureOne(eq(7L), any(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    Supplier<ResolvedVocabularyTheme> resolver = invocation.getArgument(2);
                    assertEquals(theme, resolver.get());
                    return outcome("word", "card_1", "created", "generating", true, "theme_user_1");
                });

        service.capture(7L, new VocabularyCaptureRequest(
                "req-theme", List.of("word", "other"), "en", "theme_user_1", null,
                new VocabularyCaptureRequest.Source("manual", null, null, null, null, null)));

        verify(themeService).resolve(7L, "theme_user_1", null);
        verify(itemService).captureOne(eq(7L), eq(new VocabularyCaptureRequest(
                "req-theme", List.of("word", "other"), "en", "theme_user_1", null,
                new VocabularyCaptureRequest.Source("manual", null, null, null, null, null))), any(), eq(0));
        verify(itemService).captureOne(eq(7L), eq(new VocabularyCaptureRequest(
                "req-theme", List.of("word", "other"), "en", "theme_user_1", null,
                new VocabularyCaptureRequest.Source("manual", null, null, null, null, null))), any(), eq(1));
        verify(themeMapper).recordRecentUse(7L, "theme_user_1");
    }

    @Test
    void recordsRecentThemeAfterReviewRequiredCaptureCreatesACard() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("word", "card_1", "needs_review", "needs_review", true, "theme_user_1"));

        service.capture(7L, new VocabularyCaptureRequest(
                "req-review", List.of("word"), "en", "theme_user_1", null,
                new VocabularyCaptureRequest.Source("manual", null, null, null, null, null)));

        verify(themeMapper).recordRecentUse(7L, "theme_user_1");
    }

    @Test
    void idempotentReplayDoesNotResolveOrRecordTheCurrentDefaultTheme() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("innovative", "card_1", "source_merged", "ready", false));

        VocabularyCaptureResponse response = service.capture(7L,
                VocabularyCaptureRequest.manual("req-replay", List.of("innovative"), "en", null));

        assertEquals("source_merged", response.items().get(0).action());
        verifyNoInteractions(themeService, themeMapper);
    }

    @Test
    void recordsRestoredCardsFrozenThemeInsteadOfCurrentDefaultTheme() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome(
                        "innovative", "card_1", "source_merged", "ready", true, "theme_user_1"));

        service.capture(7L,
                VocabularyCaptureRequest.manual("req-restore", List.of("innovative"), "en", null));

        verify(themeMapper).recordRecentUse(7L, "theme_user_1");
        verifyNoInteractions(themeService);
    }

    @Test
    void bulkCapturePropagatesDatabaseAndInfrastructureFailures() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(itemService.captureOne(eq(7L), any(), any(), eq(0))).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.capture(
                7L,
                VocabularyCaptureRequest.manual("req-db", List.of("word"), "en", "basic")));

        assertSame(failure, thrown);
    }

    @Test
    void rejectsInvalidRequestEnvelopeBeforeStartingItems() {
        assertThrows(IllegalArgumentException.class, () -> service.capture(null,
                VocabularyCaptureRequest.manual("req", List.of("word"), "en", "basic")));
        assertThrows(IllegalArgumentException.class, () -> service.capture(7L,
                VocabularyCaptureRequest.manual(" ", List.of("word"), "en", "basic")));
        assertThrows(IllegalArgumentException.class, () -> service.capture(7L,
                VocabularyCaptureRequest.manual("req", List.of(), "en", "basic")));
        VocabularyCaptureRequest unsupported = new VocabularyCaptureRequest(
                "req", List.of("word"), "en", null, "basic",
                new VocabularyCaptureRequest.Source("assistant", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.capture(7L, unsupported));
    }

    @Test
    void keepsLegacyJsonAndSixArgumentConstructorCompatible() throws Exception {
        VocabularyCaptureRequest fromJson = new ObjectMapper().readValue("""
                {"clientRequestId":"req-json","terms":["word"],"language":"en","source":{"type":"manual"}}
                """, VocabularyCaptureRequest.class);
        VocabularyCaptureRequest fromConstructor = new VocabularyCaptureRequest(
                "req-java", List.of("word"), "en", null, "basic",
                new VocabularyCaptureRequest.Source("manual", null, null, null, null, Map.of()));

        assertNull(fromJson.itemSources());
        assertEquals(List.of(), fromConstructor.itemSources());
    }

    @Test
    void rejectsIncompleteOrMisalignedOcrItemSourcesBeforeStartingItems() {
        VocabularyCaptureRequest missing = new VocabularyCaptureRequest(
                "req-missing", List.of("receive"), "en", null, "basic", ocrSource(), List.of());
        VocabularyCaptureRequest misaligned = new VocabularyCaptureRequest(
                "req-misaligned", List.of("receive", "package"), "en", null, "basic", ocrSource(),
                List.of(itemSource("context", "receive", "accepted")));

        assertThrows(IllegalArgumentException.class, () -> service.capture(7L, missing));
        assertThrows(IllegalArgumentException.class, () -> service.capture(7L, misaligned));
        verifyNoInteractions(itemService);
    }

    @Test
    void rejectsUnknownNonScalarOversizedAndInvalidOcrMetadataBeforeStartingItems() {
        List<VocabularyCaptureRequest> invalidRequests = List.of(
                ocrRequest(Map.of("rawText", "private"), itemMetadata("receive", "accepted")),
                ocrRequest(Map.of("imageBase64", "private"), itemMetadata("receive", "accepted")),
                ocrRequest(Map.of("recognitionTraceId", List.of("trace-1")), itemMetadata("receive", "accepted")),
                ocrRequest(Map.of("recognitionTraceId", "x".repeat(129)), itemMetadata("receive", "accepted")),
                ocrRequest(batchMetadata(), Map.of("unknown", "value")),
                ocrRequest(batchMetadata(), Map.of("observedText", Map.of("nested", true), "resolution", "accepted")),
                ocrRequest(batchMetadata(), Map.of("observedText", "receive", "resolution", "automatic")));

        for (VocabularyCaptureRequest request : invalidRequests) {
            assertThrows(IllegalArgumentException.class, () -> service.capture(7L, request));
        }
        verifyNoInteractions(itemService);
    }

    @Test
    void acceptsCompleteOcrSourcesWithStrictMetadata() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("receive", "card_1", "created", "generating", true, "theme_system_basic"));
        VocabularyCaptureRequest request = ocrRequest(batchMetadata(), itemMetadata("recieve", "suggestion_applied"));

        VocabularyCaptureResponse response = service.capture(7L, request);

        assertEquals("created", response.items().get(0).action());
        verify(itemService).captureOne(eq(7L), eq(request), any(), eq(0));
    }

    @Test
    void dictionaryFavoriteUsesCanonicalLanguageAndStableSourceReference() {
        when(itemService.captureOneInCallerTransaction(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("innovative", "card_1", "created", "generating", true, "theme_system_basic"));
        ArgumentCaptor<VocabularyCaptureRequest> requestCaptor = ArgumentCaptor.forClass(VocabularyCaptureRequest.class);

        service.captureDictionaryFavorite(7L, "In·nova·tive", "en-gb", "context");

        verify(itemService).captureOneInCallerTransaction(eq(7L), requestCaptor.capture(), any(), eq(0));
        VocabularyCaptureRequest request = requestCaptor.getValue();
        assertTrue(request.clientRequestId().startsWith("dictionary-favorite-"));
        assertEquals("en", request.language());
        assertEquals("dictionary", request.source().type());
        assertEquals("dictionary:innovative", request.source().sourceRef());
        assertEquals("词典收藏", request.source().sourceTitle());
    }

    @Test
    void repeatedDictionaryFavoritesUseOneBoundedIdempotencyPath() {
        when(itemService.captureOneInCallerTransaction(any(), any(), any(), eq(0)))
                .thenReturn(outcome("innovative", "card_1", "created", "generating", true, "theme_system_basic"));
        ArgumentCaptor<VocabularyCaptureRequest> repeatedCaptor =
                ArgumentCaptor.forClass(VocabularyCaptureRequest.class);
        ArgumentCaptor<VocabularyCaptureRequest> otherUserCaptor =
                ArgumentCaptor.forClass(VocabularyCaptureRequest.class);

        service.captureDictionaryFavorite(7L, "In·nova·tive", "en-gb", null);
        service.captureDictionaryFavorite(7L, "innovative", "en-us", null);
        service.captureDictionaryFavorite(8L, "innovative", "en-us", null);

        verify(itemService, times(2)).captureOneInCallerTransaction(eq(7L), repeatedCaptor.capture(), any(), eq(0));
        verify(itemService).captureOneInCallerTransaction(eq(8L), otherUserCaptor.capture(), any(), eq(0));
        List<VocabularyCaptureRequest> repeatedRequests = repeatedCaptor.getAllValues();
        String requestId = repeatedRequests.get(0).clientRequestId();
        assertEquals(requestId, repeatedRequests.get(1).clientRequestId());
        assertNotEquals(requestId, otherUserCaptor.getValue().clientRequestId());
        assertTrue(requestId.matches("dictionary-favorite-7-[0-9a-f]{64}"));
        assertTrue(requestId.length() <= 128);
        assertEquals("dictionary:innovative", repeatedRequests.get(0).source().sourceRef());
        assertEquals(repeatedRequests.get(0).source().sourceRef(), repeatedRequests.get(1).source().sourceRef());
    }

    @Test
    void dictionaryFavoritePropagatesItemFailure() {
        RuntimeException failure = new RuntimeException("job insert failed");
        when(itemService.captureOneInCallerTransaction(eq(7L), any(), any(), eq(0))).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.captureDictionaryFavorite(7L, "innovative", "en-gb", null));

        assertSame(failure, thrown);
    }

    @Test
    void dictionaryFavoriteCaptureIsTransactional() throws NoSuchMethodException {
        Method method = VocabularyCaptureService.class.getMethod(
                "captureDictionaryFavorite", Long.class, String.class, String.class, String.class);

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    @Test
    void recordsSafeCaptureAndImmediateReadyEventsWithRecognitionTrace() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("receive", "card_1", "source_merged", "ready", false));
        VocabularyCaptureRequest request = ocrRequest(
                batchMetadata(), itemMetadata("recieve", "suggestion_applied"));

        VocabularyCaptureResponse response = service.capture(7L, request);

        assertEquals("ready", response.items().get(0).status());
        ArgumentCaptor<VocabularyProductEventService.ServerEvent> events =
                ArgumentCaptor.forClass(VocabularyProductEventService.ServerEvent.class);
        verify(productEventService, times(2)).recordServerEvent(eq(7L), events.capture());
        assertEquals("vocabulary_capture_submitted", events.getAllValues().get(0).eventName());
        assertEquals("trace-1", events.getAllValues().get(0).traceId());
        assertEquals(Map.of("sourceType", "ocr_image", "successCount", 1, "failedCount", 0),
                events.getAllValues().get(0).properties());
        assertEquals("vocabulary_cards_ready", events.getAllValues().get(1).eventName());
        assertEquals("card_1", events.getAllValues().get(1).cardUid());
        assertTrue(events.getAllValues().stream().noneMatch(event ->
                event.properties().keySet().stream().anyMatch(key -> key.equalsIgnoreCase("observedText"))));
    }

    @Test
    void analyticsFailureDoesNotRollbackSuccessfulCapture() {
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome(
                        "safe", "card_1", "created", "generating", true, "theme_system_basic"));
        when(productEventService.recordServerEvent(eq(7L), any()))
                .thenThrow(new RuntimeException("analytics unavailable"));

        Logger logger = (Logger) LoggerFactory.getLogger(VocabularyCaptureService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            VocabularyCaptureResponse response = service.capture(
                    7L, VocabularyCaptureRequest.manual(
                            "private filename receive.png", List.of("safe"), "en", "basic"));

            assertEquals("card_1", response.items().get(0).cardUid());
            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains("private filename receive.png")));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    private ResolvedVocabularyTheme theme(String themeUid, int version, String templateKey) {
        return new ResolvedVocabularyTheme(themeUid, version, "Theme", "Purpose", "strategy", 1, templateKey);
    }

    private VocabularyCaptureItemService.CaptureOutcome outcome(
            String term, String cardUid, String action, String status, boolean mutated) {
        return new VocabularyCaptureItemService.CaptureOutcome(
                new VocabularyCaptureResponse.Item(term, cardUid, action, status), mutated, null);
    }

    private VocabularyCaptureItemService.CaptureOutcome outcome(
            String term, String cardUid, String action, String status, boolean mutated, String themeUid) {
        return new VocabularyCaptureItemService.CaptureOutcome(
                new VocabularyCaptureResponse.Item(term, cardUid, action, status), mutated, themeUid);
    }

    private VocabularyCaptureRequest ocrRequest(
            Map<String, Object> batchMetadata, Map<String, Object> itemMetadata) {
        return new VocabularyCaptureRequest(
                "req-ocr", List.of("receive"), "en", null, "basic",
                new VocabularyCaptureRequest.Source(
                        "ocr_image", null, "图片识别", null, "batch context", batchMetadata),
                List.of(new VocabularyCaptureRequest.ItemSource("item context", itemMetadata)));
    }

    private VocabularyCaptureRequest.Source ocrSource() {
        return new VocabularyCaptureRequest.Source(
                "ocr_image", null, "图片识别", null, null, batchMetadata());
    }

    private VocabularyCaptureRequest.ItemSource itemSource(
            String contextText, String observedText, String resolution) {
        return new VocabularyCaptureRequest.ItemSource(
                contextText, itemMetadata(observedText, resolution));
    }

    private Map<String, Object> batchMetadata() {
        return Map.of(
                "recognitionTraceId", "trace-1",
                "fileName", "words.png",
                "provider", "openai",
                "model", "vision-model",
                "promptVersion", "vocabulary-image-recognition-v1");
    }

    private Map<String, Object> itemMetadata(String observedText, String resolution) {
        return Map.of("observedText", observedText, "resolution", resolution);
    }
}
