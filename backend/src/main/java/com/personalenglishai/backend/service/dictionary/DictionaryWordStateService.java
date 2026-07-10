package com.personalenglishai.backend.service.dictionary;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryFavoriteItemResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryWordStateResponse;
import com.personalenglishai.backend.entity.UserDictionaryWordState;
import com.personalenglishai.backend.mapper.DictionaryContentMapper;
import com.personalenglishai.backend.mapper.UserDictionaryWordStateMapper;
import com.personalenglishai.backend.service.vocabulary.VocabularyCaptureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DictionaryWordStateService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryWordStateService.class);

    private final UserDictionaryWordStateMapper mapper;
    private final DictionaryContentMapper dictionaryContentMapper;
    private final VocabularyCaptureService vocabularyCaptureService;

    public DictionaryWordStateService(UserDictionaryWordStateMapper mapper,
                                      DictionaryContentMapper dictionaryContentMapper,
                                      VocabularyCaptureService vocabularyCaptureService) {
        this.mapper = mapper;
        this.dictionaryContentMapper = dictionaryContentMapper;
        this.vocabularyCaptureService = vocabularyCaptureService;
    }

    public void attachLookupState(Long userId, DictionaryLookupResponse response, String requestedWord, String language) {
        if (userId == null || response == null) {
            return;
        }
        String word = firstNonBlank(response.getWord(), requestedWord);
        String normalizedWord = normalizeWord(word);
        if (normalizedWord.isBlank()) {
            return;
        }
        try {
            mapper.incrementLookup(userId, word, normalizedWord, firstNonBlank(response.getLanguage(), language), response.getSource());
            UserDictionaryWordState state = mapper.selectByUserAndWord(userId, normalizedWord);
            applyState(response, state);
        } catch (RuntimeException e) {
            log.warn("Failed to update dictionary word lookup state. userId={} word={} reason={}",
                    userId, word, e.getMessage());
        }
    }

    public DictionaryWordStateResponse setFavorite(Long userId, String word, String language, boolean favorite) {
        String normalizedWord = normalizeWord(word);
        if (userId == null || normalizedWord.isBlank()) {
            throw new IllegalArgumentException("invalid user or word");
        }
        String displayWord = word.trim();
        String effectiveLanguage = firstNonBlank(language, "en-gb");
        mapper.setFavorite(userId, displayWord, normalizedWord, effectiveLanguage, favorite);
        if (favorite) {
            vocabularyCaptureService.captureDictionaryFavorite(userId, displayWord, effectiveLanguage, null);
        }
        return toResponse(mapper.selectByUserAndWord(userId, normalizedWord), displayWord, effectiveLanguage);
    }

    public AdminPageResponse<DictionaryFavoriteItemResponse> listFavorites(Long userId,
                                                                           String keyword,
                                                                           Integer page,
                                                                           Integer size) {
        if (userId == null) {
            throw new IllegalArgumentException("invalid user");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 10 : Math.max(1, Math.min(size, 50));
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        int offset = (safePage - 1) * safeSize;
        List<UserDictionaryWordState> states = mapper.selectFavorites(userId, normalizedKeyword, offset, safeSize);
        long total = mapper.countFavorites(userId, normalizedKeyword);
        List<DictionaryFavoriteItemResponse> items = states.stream()
                .map(this::toFavoriteItem)
                .toList();
        return new AdminPageResponse<>(items, total, safePage, safeSize);
    }

    private void applyState(DictionaryLookupResponse response, UserDictionaryWordState state) {
        if (state == null) {
            response.setFavorite(false);
            response.setLookupCount(0);
            return;
        }
        response.setFavorite(Boolean.TRUE.equals(state.getFavorite()));
        response.setLookupCount(state.getLookupCount() == null ? 0 : state.getLookupCount());
    }

    private DictionaryWordStateResponse toResponse(UserDictionaryWordState state, String fallbackWord, String fallbackLanguage) {
        DictionaryWordStateResponse response = new DictionaryWordStateResponse();
        response.setWord(state == null ? fallbackWord : firstNonBlank(state.getWord(), fallbackWord));
        response.setLanguage(state == null ? fallbackLanguage : firstNonBlank(state.getLanguage(), fallbackLanguage));
        response.setFavorite(state != null && Boolean.TRUE.equals(state.getFavorite()));
        response.setLookupCount(state == null || state.getLookupCount() == null ? 0 : state.getLookupCount());
        return response;
    }

    private DictionaryFavoriteItemResponse toFavoriteItem(UserDictionaryWordState state) {
        DictionaryFavoriteItemResponse response = new DictionaryFavoriteItemResponse();
        response.setWord(firstNonBlank(state.getWord(), state.getNormalizedWord()));
        response.setLanguage(state.getLanguage());
        response.setSource(state.getSource());
        response.setFavorite(Boolean.TRUE.equals(state.getFavorite()));
        response.setLookupCount(state.getLookupCount() == null ? 0 : state.getLookupCount());
        response.setFavoritedAt(state.getFavoritedAt());
        response.setLastLookupAt(state.getLastLookupAt());
        applyDictionaryPreview(response, state.getNormalizedWord());
        return response;
    }

    private void applyDictionaryPreview(DictionaryFavoriteItemResponse response, String normalizedWord) {
        if (normalizedWord == null || normalizedWord.isBlank()) {
            return;
        }
        List<Map<String, Object>> entries = dictionaryContentMapper.selectActiveEntriesByNormalizedHeadword(normalizedWord);
        if (entries.isEmpty()) {
            return;
        }
        Map<String, Object> entry = entries.get(0);
        String entryUid = stringValue(entry.get("entryUid"));
        response.setPartOfSpeech(stringValue(entry.get("partOfSpeech")));
        response.setMeaning(firstNonBlank(dictionarySensePreview(entryUid), stringValue(entry.get("cleanText"))));
        if (!entryUid.isBlank()) {
            List<Map<String, Object>> pronunciations = dictionaryContentMapper.selectPronunciationsByEntryUids(List.of(entryUid));
            if (!pronunciations.isEmpty()) {
                response.setPhonetic(stringValue(pronunciations.get(0).get("phonetic")));
            }
        }
    }

    private String dictionarySensePreview(String entryUid) {
        if (entryUid == null || entryUid.isBlank()) {
            return "";
        }
        List<Map<String, Object>> senses = dictionaryContentMapper.selectSensesByEntryUids(List.of(entryUid));
        if (senses.isEmpty()) {
            return "";
        }
        Map<String, Object> sense = senses.get(0);
        return joinBilingual(stringValue(sense.get("definitionEn")), stringValue(sense.get("definitionZh")));
    }

    private String joinBilingual(String english, String chinese) {
        String left = firstNonBlank(english, "");
        String right = firstNonBlank(chinese, "");
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank()) {
            return left;
        }
        return left + "；" + right;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeWord(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? (fallback == null ? "" : fallback.trim()) : preferred.trim();
    }
}
