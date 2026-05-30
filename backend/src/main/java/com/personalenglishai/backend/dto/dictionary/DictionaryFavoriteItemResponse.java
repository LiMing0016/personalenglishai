package com.personalenglishai.backend.dto.dictionary;

import java.time.LocalDateTime;

public class DictionaryFavoriteItemResponse {
    private String word;
    private String language;
    private String source;
    private Boolean favorite;
    private Integer lookupCount;
    private LocalDateTime favoritedAt;
    private LocalDateTime lastLookupAt;
    private String phonetic;
    private String partOfSpeech;
    private String meaning;

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

    public LocalDateTime getFavoritedAt() {
        return favoritedAt;
    }

    public void setFavoritedAt(LocalDateTime favoritedAt) {
        this.favoritedAt = favoritedAt;
    }

    public LocalDateTime getLastLookupAt() {
        return lastLookupAt;
    }

    public void setLastLookupAt(LocalDateTime lastLookupAt) {
        this.lastLookupAt = lastLookupAt;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }
}
