package com.personalenglishai.backend.service.dictionary;

import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.mapper.DictionaryContentMapper;
import com.personalenglishai.backend.service.dictionary.impl.LocalFirstDictionaryLookupService;
import com.personalenglishai.backend.service.dictionary.impl.OxfordDictionaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LocalFirstDictionaryLookupService")
class LocalFirstDictionaryLookupServiceTest {

    private final DictionaryContentMapper dictionaryContentMapper = mock(DictionaryContentMapper.class);
    private final OxfordDictionaryService oxfordDictionaryService = mock(OxfordDictionaryService.class);
    private final LocalFirstDictionaryLookupService service = new LocalFirstDictionaryLookupService(
            dictionaryContentMapper,
            oxfordDictionaryService
    );

    @Test
    @DisplayName("returns local dictionary content before calling Oxford")
    void returnsLocalDictionaryContentBeforeCallingOxford() {
        when(dictionaryContentMapper.selectActiveEntriesByNormalizedHeadword("home")).thenReturn(List.of(Map.of(
                "entryUid", "entry_home",
                "headword", "home",
                "partOfSpeech", "noun",
                "dictionaryName", "牛津高阶英汉双解词典（第9版）"
        )));
        when(dictionaryContentMapper.selectPronunciationsByEntryUids(List.of("entry_home"))).thenReturn(List.of(Map.of(
                "entryUid", "entry_home",
                "phonetic", "/həʊm/"
        )));
        when(dictionaryContentMapper.selectSensesByEntryUids(List.of("entry_home"))).thenReturn(List.of(Map.of(
                "entryUid", "entry_home",
                "partOfSpeech", "noun",
                "definitionEn", "the house or flat that you live in",
                "definitionZh", "家；住所"
        )));
        when(dictionaryContentMapper.selectExamplesByEntryUids(List.of("entry_home"))).thenReturn(List.of(Map.of(
                "entryUid", "entry_home",
                "textEn", "We are not far from home now.",
                "textZh", "我们现在离家不远了。"
        )));
        when(dictionaryContentMapper.selectPhrasesByEntryUids(List.of("entry_home"))).thenReturn(List.of());

        DictionaryLookupResponse response = service.lookup(" Home ", "en-gb");

        assertThat(response.getSource()).isEqualTo("local");
        assertThat(response.getWord()).isEqualTo("home");
        assertThat(response.getLanguage()).isEqualTo("en-gb");
        assertThat(response.getPhonetics()).hasSize(1);
        assertThat(response.getPhonetics().get(0).getText()).isEqualTo("/həʊm/");
        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getEntries().get(0).getPartOfSpeech()).isEqualTo("noun");
        assertThat(response.getEntries().get(0).getDefinitions())
                .contains("the house or flat that you live in；家；住所");
        assertThat(response.getEntries().get(0).getExamples())
                .contains("We are not far from home now. 我们现在离家不远了。");
        verify(oxfordDictionaryService, never()).lookup("Home", "en-gb");
    }

    @Test
    @DisplayName("falls back to Oxford when local dictionary has no entry")
    void fallsBackToOxfordWhenLocalDictionaryHasNoEntry() {
        DictionaryLookupResponse fallback = new DictionaryLookupResponse();
        fallback.setWord("missing");
        fallback.setLanguage("en-gb");
        fallback.setSource("oxford");

        when(dictionaryContentMapper.selectActiveEntriesByNormalizedHeadword("missing")).thenReturn(List.of());
        when(oxfordDictionaryService.lookup("missing", "en-gb")).thenReturn(fallback);

        DictionaryLookupResponse response = service.lookup("missing", "en-gb");

        assertThat(response).isSameAs(fallback);
        verify(oxfordDictionaryService).lookup("missing", "en-gb");
    }
}
