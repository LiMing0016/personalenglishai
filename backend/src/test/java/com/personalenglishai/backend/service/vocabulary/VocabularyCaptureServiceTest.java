package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyThemeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessResourceFailureException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    VocabularyCaptureService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyCaptureService(itemService, themeService, themeMapper, new VocabularyTermNormalizer());
    }

    @Test
    void bulkCaptureKeepsSuccessfulItemsWhenOneItemHasKnownRejection() {
        ResolvedVocabularyTheme theme = theme("theme_system_basic", 1, "basic");
        when(themeService.resolve(7L, null, "basic")).thenReturn(theme);
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("good", "card_1", "created", "generating", true));
        when(itemService.captureOne(eq(7L), any(), any(), eq(1)))
                .thenThrow(new VocabularyCaptureRejectedException("invalid term"));

        var result = service.capture(7L,
                VocabularyCaptureRequest.manual("req-bulk", List.of("good", "bad"), "en", "basic"));

        assertEquals(List.of("created", "rejected"),
                result.items().stream().map(VocabularyCaptureResponse.Item::action).toList());
        verify(themeService).resolve(7L, null, "basic");
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
                    return outcome("word", "card_1", "created", "generating", true);
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
        ResolvedVocabularyTheme theme = theme("theme_user_1", 3, "basic");
        when(themeService.resolve(7L, "theme_user_1", null)).thenReturn(theme);
        when(itemService.captureOne(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("word", "card_1", "needs_review", "needs_review", true));

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
    void dictionaryFavoriteUsesCanonicalLanguageAndStableSourceReference() {
        ResolvedVocabularyTheme theme = theme("theme_system_basic", 1, "basic");
        when(themeService.resolve(7L, null, null)).thenReturn(theme);
        when(itemService.captureOneInCallerTransaction(eq(7L), any(), any(), eq(0)))
                .thenReturn(outcome("innovative", "card_1", "created", "generating", true));
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
        when(themeService.resolve(anyLong(), eq(null), eq(null)))
                .thenReturn(theme("theme_system_basic", 1, "basic"));
        when(itemService.captureOneInCallerTransaction(any(), any(), any(), eq(0)))
                .thenReturn(outcome("innovative", "card_1", "created", "generating", true));
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

    private ResolvedVocabularyTheme theme(String themeUid, int version, String templateKey) {
        return new ResolvedVocabularyTheme(themeUid, version, "Theme", "Purpose", "strategy", 1, templateKey);
    }

    private VocabularyCaptureItemService.CaptureOutcome outcome(
            String term, String cardUid, String action, String status, boolean mutated) {
        return new VocabularyCaptureItemService.CaptureOutcome(
                new VocabularyCaptureResponse.Item(term, cardUid, action, status), mutated);
    }
}
