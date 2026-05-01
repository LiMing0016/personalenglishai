package com.personalenglishai.backend.service.dictionary;

import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;

public interface DictionaryLookupService {
    DictionaryLookupResponse lookup(String word, String language);
}
