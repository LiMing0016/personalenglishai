package com.personalenglishai.backend.service.dictionary;

import com.personalenglishai.backend.entity.UserDictionaryWordState;
import com.personalenglishai.backend.mapper.DictionaryContentMapper;
import com.personalenglishai.backend.mapper.UserDictionaryWordStateMapper;
import com.personalenglishai.backend.service.vocabulary.VocabularyCaptureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryWordStateServiceTest {

    @Mock
    private UserDictionaryWordStateMapper mapper;

    @Mock
    private DictionaryContentMapper contentMapper;

    @Mock
    private VocabularyCaptureService captureService;

    @Test
    void favoriteCapturesAfterPersistingButUnfavoriteDoesNotCapture() {
        DictionaryWordStateService service = new DictionaryWordStateService(mapper, contentMapper, captureService);
        UserDictionaryWordState state = new UserDictionaryWordState();
        state.setWord("innovative");
        state.setLanguage("en-gb");
        state.setFavorite(true);
        state.setLookupCount(1);
        when(mapper.selectByUserAndWord(7L, "innovative")).thenReturn(state);

        service.setFavorite(7L, "innovative", "en-gb", true);
        service.setFavorite(7L, "innovative", "en-gb", false);

        InOrder inOrder = inOrder(mapper, captureService);
        inOrder.verify(mapper).setFavorite(7L, "innovative", "innovative", "en-gb", true);
        inOrder.verify(captureService).captureDictionaryFavorite(7L, "innovative", "en-gb", null);
        inOrder.verify(mapper).setFavorite(7L, "innovative", "innovative", "en-gb", false);
        verify(captureService, times(1)).captureDictionaryFavorite(7L, "innovative", "en-gb", null);
    }
}
