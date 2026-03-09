package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranslateResponse {

    /** Full mode: entire translated text */
    private String translation;

    /** Detailed mode: sentence-by-sentence translation with analysis */
    private List<SentenceTranslation> sentences;

    public TranslateResponse() {}

    public TranslateResponse(String translation) {
        this.translation = translation;
    }

    public TranslateResponse(List<SentenceTranslation> sentences) {
        this.sentences = sentences;
    }

    public String getTranslation() { return translation; }
    public void setTranslation(String translation) { this.translation = translation; }

    public List<SentenceTranslation> getSentences() { return sentences; }
    public void setSentences(List<SentenceTranslation> sentences) { this.sentences = sentences; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HighlightItem {
        private String word;
        private String meaning;
        private String detail;

        public HighlightItem() {}

        public HighlightItem(String word, String meaning, String detail) {
            this.word = word;
            this.meaning = meaning;
            this.detail = detail;
        }

        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }

        public String getMeaning() { return meaning; }
        public void setMeaning(String meaning) { this.meaning = meaning; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SentenceTranslation {
        private String english;
        private String chinese;
        private String structure;
        private List<HighlightItem> highlights;

        public SentenceTranslation() {}

        public String getEnglish() { return english; }
        public void setEnglish(String english) { this.english = english; }

        public String getChinese() { return chinese; }
        public void setChinese(String chinese) { this.chinese = chinese; }

        public String getStructure() { return structure; }
        public void setStructure(String structure) { this.structure = structure; }

        public List<HighlightItem> getHighlights() { return highlights; }
        public void setHighlights(List<HighlightItem> highlights) { this.highlights = highlights; }
    }
}
