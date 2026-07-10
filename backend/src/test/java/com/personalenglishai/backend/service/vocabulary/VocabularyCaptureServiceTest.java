package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyCaptureServiceTest {
    @Mock VocabularyCaptureItemService itemService;
    VocabularyCaptureService service;

    @BeforeEach
    void setUp() {
        service = new VocabularyCaptureService(itemService, new VocabularyTermNormalizer());
    }

    @Test
    void bulkCaptureKeepsSuccessfulItemsWhenOneItemFails() {
        when(itemService.captureOne(eq(7L), any(), eq(0)))
                .thenReturn(new VocabularyCaptureResponse.Item("good", "card_1", "created", "generating"));
        when(itemService.captureOne(eq(7L), any(), eq(1))).thenThrow(new RuntimeException("db unavailable"));

        var result = service.capture(7L,
                VocabularyCaptureRequest.manual("req-bulk", List.of("good", "bad"), "en", "basic"));

        assertEquals(List.of("created", "rejected"),
                result.items().stream().map(VocabularyCaptureResponse.Item::action).toList());
        verify(itemService, never()).captureOneInCallerTransaction(anyLong(), any(), anyInt());
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
                "req", List.of("word"), "en", "basic",
                new VocabularyCaptureRequest.Source("assistant", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.capture(7L, unsupported));
    }

    @Test
    void dictionaryFavoriteUsesCanonicalLanguageAndStableSourceReference() {
        when(itemService.captureOneInCallerTransaction(eq(7L), any(), eq(0)))
                .thenReturn(new VocabularyCaptureResponse.Item("innovative", "card_1", "created", "generating"));
        ArgumentCaptor<VocabularyCaptureRequest> requestCaptor = ArgumentCaptor.forClass(VocabularyCaptureRequest.class);

        service.captureDictionaryFavorite(7L, "In·nova·tive", "en-gb", "context");

        verify(itemService).captureOneInCallerTransaction(eq(7L), requestCaptor.capture(), eq(0));
        verify(itemService, never()).captureOne(anyLong(), any(), anyInt());
        VocabularyCaptureRequest request = requestCaptor.getValue();
        assertTrue(request.clientRequestId().startsWith("dictionary-favorite-"));
        assertEquals("en", request.language());
        assertEquals("dictionary", request.source().type());
        assertEquals("dictionary:innovative", request.source().sourceRef());
        assertEquals("词典收藏", request.source().sourceTitle());
    }

    @Test
    void repeatedDictionaryFavoritesUseOneBoundedIdempotencyPath() {
        when(itemService.captureOneInCallerTransaction(anyLong(), any(), eq(0)))
                .thenReturn(new VocabularyCaptureResponse.Item("innovative", "card_1", "created", "generating"));
        ArgumentCaptor<VocabularyCaptureRequest> repeatedCaptor =
                ArgumentCaptor.forClass(VocabularyCaptureRequest.class);
        ArgumentCaptor<VocabularyCaptureRequest> otherUserCaptor =
                ArgumentCaptor.forClass(VocabularyCaptureRequest.class);

        service.captureDictionaryFavorite(7L, "In·nova·tive", "en-gb", null);
        service.captureDictionaryFavorite(7L, "innovative", "en-us", null);
        service.captureDictionaryFavorite(8L, "innovative", "en-us", null);

        verify(itemService, times(2)).captureOneInCallerTransaction(eq(7L), repeatedCaptor.capture(), eq(0));
        verify(itemService).captureOneInCallerTransaction(eq(8L), otherUserCaptor.capture(), eq(0));
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
        when(itemService.captureOneInCallerTransaction(eq(7L), any(), eq(0))).thenThrow(failure);

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
}
