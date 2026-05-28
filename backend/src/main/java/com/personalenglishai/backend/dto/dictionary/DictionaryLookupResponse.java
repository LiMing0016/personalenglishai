package com.personalenglishai.backend.dto.dictionary;

import java.util.ArrayList;
import java.util.List;

public class DictionaryLookupResponse {
    private String word;
    private String language;
    private String source = "oxford";
    private List<DictionaryPhoneticDto> phonetics = new ArrayList<>();
    private List<DictionaryEntryDto> entries = new ArrayList<>();
    private Boolean favorite;
    private Integer lookupCount;

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<DictionaryPhoneticDto> getPhonetics() {
        return phonetics;
    }

    public void setPhonetics(List<DictionaryPhoneticDto> phonetics) {
        this.phonetics = phonetics == null ? new ArrayList<>() : phonetics;
    }

    public List<DictionaryEntryDto> getEntries() {
        return entries;
    }

    public void setEntries(List<DictionaryEntryDto> entries) {
        this.entries = entries == null ? new ArrayList<>() : entries;
    }

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public Integer getLookupCount() {
        return lookupCount;
    }

    public void setLookupCount(Integer lookupCount) {
        this.lookupCount = lookupCount;
    }
}
