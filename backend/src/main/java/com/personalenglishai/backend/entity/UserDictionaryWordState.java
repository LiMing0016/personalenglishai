package com.personalenglishai.backend.entity;

import java.time.LocalDateTime;

public class UserDictionaryWordState {
    private Long id;
    private Long userId;
    private String word;
    private String normalizedWord;
    private String language;
    private String source;
    private Boolean favorite;
    private Integer lookupCount;
    private LocalDateTime favoritedAt;
    private LocalDateTime lastLookupAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getNormalizedWord() {
        return normalizedWord;
    }

    public void setNormalizedWord(String normalizedWord) {
        this.normalizedWord = normalizedWord;
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
}
