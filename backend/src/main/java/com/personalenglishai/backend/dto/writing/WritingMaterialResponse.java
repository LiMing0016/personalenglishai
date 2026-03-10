package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WritingMaterialResponse {

    private String topic;
    private String stage;
    private List<VocabularyGroup> vocabulary;
    private List<PhraseItem> phrases;
    private List<SentenceItem> sentences;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public List<VocabularyGroup> getVocabulary() { return vocabulary; }
    public void setVocabulary(List<VocabularyGroup> vocabulary) { this.vocabulary = vocabulary; }

    public List<PhraseItem> getPhrases() { return phrases; }
    public void setPhrases(List<PhraseItem> phrases) { this.phrases = phrases; }

    public List<SentenceItem> getSentences() { return sentences; }
    public void setSentences(List<SentenceItem> sentences) { this.sentences = sentences; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VocabularyGroup {
        private String category;
        private List<VocabularyItem> words;

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public List<VocabularyItem> getWords() { return words; }
        public void setWords(List<VocabularyItem> words) { this.words = words; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VocabularyItem {
        private String word;
        private String meaning;

        public String getWord() { return word; }
        public void setWord(String word) { this.word = word; }

        public String getMeaning() { return meaning; }
        public void setMeaning(String meaning) { this.meaning = meaning; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PhraseItem {
        private String phrase;
        private String meaning;

        public String getPhrase() { return phrase; }
        public void setPhrase(String phrase) { this.phrase = phrase; }

        public String getMeaning() { return meaning; }
        public void setMeaning(String meaning) { this.meaning = meaning; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SentenceItem {
        private String sentence;
        private String description;

        public String getSentence() { return sentence; }
        public void setSentence(String sentence) { this.sentence = sentence; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
