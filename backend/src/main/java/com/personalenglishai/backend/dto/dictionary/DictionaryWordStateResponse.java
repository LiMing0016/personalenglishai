package com.personalenglishai.backend.dto.dictionary;

public class DictionaryWordStateResponse {
    private String word;
    private String language;
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
