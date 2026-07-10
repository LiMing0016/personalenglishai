package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyDictionaryEnricher {

    private final DictionaryLookupService dictionaryLookupService;

    public VocabularyDictionaryEnricher(DictionaryLookupService dictionaryLookupService) {
        this.dictionaryLookupService = dictionaryLookupService;
    }

    public DictionaryLookupResponse lookupWithoutUserState(String term, String language) {
        return dictionaryLookupService.lookup(term, language);
    }
}
