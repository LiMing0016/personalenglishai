package com.personalenglishai.backend.dto.dictionary;

public class DictionaryFavoriteRequest {
    private Boolean favorite;
    private String language;

    public Boolean getFavorite() {
        return favorite;
    }

    public void setFavorite(Boolean favorite) {
        this.favorite = favorite;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
