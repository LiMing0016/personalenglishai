package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureRequest;
import com.personalenglishai.backend.dto.vocabulary.VocabularyCaptureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(itemService.captureOne(eq(7L), any(), eq(0)))
                .thenReturn(new VocabularyCaptureResponse.Item("innovative", "card_1", "created", "generating"));
        ArgumentCaptor<VocabularyCaptureRequest> requestCaptor = ArgumentCaptor.forClass(VocabularyCaptureRequest.class);

        service.captureDictionaryFavorite(7L, "In·nova·tive", "en-gb", "context");

        verify(itemService).captureOne(eq(7L), requestCaptor.capture(), eq(0));
        VocabularyCaptureRequest request = requestCaptor.getValue();
        assertTrue(request.clientRequestId().startsWith("dictionary-favorite-"));
        assertEquals("en", request.language());
        assertEquals("dictionary", request.source().type());
        assertEquals("dictionary:innovative", request.source().sourceRef());
        assertEquals("词典收藏", request.source().sourceTitle());
    }
}
