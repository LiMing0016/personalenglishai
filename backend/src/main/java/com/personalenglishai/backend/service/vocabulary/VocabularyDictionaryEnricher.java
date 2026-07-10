package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyDictionaryEnricher {

    private final DictionaryLookupService dictionaryLookupService;

    public VocabularyDictionaryEnricher(DictionaryLookupService dictionaryLookupService) {
        this.dictionaryLookupService = dictionaryLookupService;
    }

    public DictionaryLookupResponse lookupWithoutUserState(String term, String language) {
        try {
            return dictionaryLookupService.lookup(term, language);
        } catch (DictionaryLookupException exception) {
            if (exception.getKind() == DictionaryLookupException.Kind.NOT_FOUND) {
                return null;
            }
            throw exception;
        }
    }
}
