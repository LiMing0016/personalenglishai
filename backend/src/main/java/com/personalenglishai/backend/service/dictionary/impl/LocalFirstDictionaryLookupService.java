package com.personalenglishai.backend.service.dictionary.impl;

import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import com.personalenglishai.backend.mapper.DictionaryContentMapper;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Primary
@Service
public class LocalFirstDictionaryLookupService implements DictionaryLookupService {

    private final DictionaryContentMapper dictionaryContentMapper;
    private final OxfordDictionaryService oxfordDictionaryService;

    public LocalFirstDictionaryLookupService(
            DictionaryContentMapper dictionaryContentMapper,
            OxfordDictionaryService oxfordDictionaryService) {
        this.dictionaryContentMapper = dictionaryContentMapper;
        this.oxfordDictionaryService = oxfordDictionaryService;
    }

    @Override
    public DictionaryLookupResponse lookup(String word, String language) {
        String normalizedInput = word == null ? "" : word.trim();
        String normalizedHeadword = normalizeHeadword(normalizedInput);
        List<Map<String, Object>> entryRows = dictionaryContentMapper.selectActiveEntriesByNormalizedHeadword(normalizedHeadword);
        if (entryRows == null || entryRows.isEmpty()) {
            return oxfordDictionaryService.lookup(normalizedInput, language);
        }
        return buildLocalResponse(normalizedInput, language, entryRows);
    }

    private DictionaryLookupResponse buildLocalResponse(String input, String language, List<Map<String, Object>> entryRows) {
        List<String> entryUids = entryRows.stream()
                .map((row) -> stringValue(row.get("entryUid")))
                .filter((value) -> !value.isBlank())
                .toList();
        Map<String, List<Map<String, Object>>> pronunciations = groupByEntryUid(dictionaryContentMapper.selectPronunciationsByEntryUids(entryUids));
        Map<String, List<Map<String, Object>>> senses = groupByEntryUid(dictionaryContentMapper.selectSensesByEntryUids(entryUids));
        Map<String, List<Map<String, Object>>> examples = groupByEntryUid(dictionaryContentMapper.selectExamplesByEntryUids(entryUids));
        Map<String, List<Map<String, Object>>> phrases = groupByEntryUid(dictionaryContentMapper.selectPhrasesByEntryUids(entryUids));

        DictionaryLookupResponse response = new DictionaryLookupResponse();
        response.setWord(firstNonBlank(stringValue(entryRows.get(0).get("headword")), input));
        response.setLanguage(firstNonBlank(language, "en-gb"));
        response.setSource("local");
        response.setPhonetics(buildPhonetics(entryUids, pronunciations));
        response.setEntries(buildEntries(entryRows, senses, examples, phrases));
        return response;
    }

    private List<DictionaryPhoneticDto> buildPhonetics(
            List<String> entryUids,
            Map<String, List<Map<String, Object>>> pronunciations) {
        List<DictionaryPhoneticDto> result = new ArrayList<>();
        for (String entryUid : entryUids) {
            for (Map<String, Object> row : pronunciations.getOrDefault(entryUid, List.of())) {
                String phonetic = stringValue(row.get("phonetic"));
                if (!phonetic.isBlank()) {
                    result.add(new DictionaryPhoneticDto(phonetic, null));
                }
            }
        }
        return result;
    }

    private List<DictionaryEntryDto> buildEntries(
            List<Map<String, Object>> entryRows,
            Map<String, List<Map<String, Object>>> senses,
            Map<String, List<Map<String, Object>>> examples,
            Map<String, List<Map<String, Object>>> phrases) {
        List<DictionaryEntryDto> result = new ArrayList<>();
        for (Map<String, Object> entryRow : entryRows) {
            String entryUid = stringValue(entryRow.get("entryUid"));
            DictionaryEntryDto entry = new DictionaryEntryDto(firstNonBlank(stringValue(entryRow.get("partOfSpeech")), "unknown"));
            List<String> definitions = new ArrayList<>();
            for (Map<String, Object> senseRow : senses.getOrDefault(entryUid, List.of())) {
                String definition = joinBilingual(stringValue(senseRow.get("definitionEn")), stringValue(senseRow.get("definitionZh")));
                if (!definition.isBlank()) {
                    definitions.add(definition);
                }
            }
            for (Map<String, Object> phraseRow : phrases.getOrDefault(entryUid, List.of())) {
                String phrase = stringValue(phraseRow.get("phraseText"));
                String definition = joinBilingual(stringValue(phraseRow.get("definitionEn")), stringValue(phraseRow.get("definitionZh")));
                if (!phrase.isBlank()) {
                    definitions.add(definition.isBlank() ? "短语：" + phrase : "短语：" + phrase + " - " + definition);
                }
            }
            if (definitions.isEmpty()) {
                String cleanText = stringValue(entryRow.get("cleanText"));
                if (!cleanText.isBlank()) {
                    definitions.add(cleanText);
                }
            }
            entry.setDefinitions(definitions);

            List<String> exampleTexts = new ArrayList<>();
            for (Map<String, Object> exampleRow : examples.getOrDefault(entryUid, List.of())) {
                String example = joinWithSpace(stringValue(exampleRow.get("textEn")), stringValue(exampleRow.get("textZh")));
                if (!example.isBlank()) {
                    exampleTexts.add(example);
                }
            }
            entry.setExamples(exampleTexts);
            result.add(entry);
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> groupByEntryUid(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        if (rows == null) {
            return grouped;
        }
        for (Map<String, Object> row : rows) {
            String entryUid = stringValue(row.get("entryUid"));
            if (!entryUid.isBlank()) {
                grouped.computeIfAbsent(entryUid, ignored -> new ArrayList<>()).add(row);
            }
        }
        return grouped;
    }

    private String normalizeHeadword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private String joinBilingual(String english, String chinese) {
        if (english.isBlank()) {
            return chinese;
        }
        if (chinese.isBlank()) {
            return english;
        }
        return english + "；" + chinese;
    }

    private String joinWithSpace(String first, String second) {
        if (first.isBlank()) {
            return second;
        }
        if (second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }
}
