package com.personalenglishai.backend.dto.dictionary;

import java.util.ArrayList;
import java.util.List;

public class DictionaryEntryDto {
    private String partOfSpeech;
    private List<String> definitions = new ArrayList<>();
    private List<String> examples = new ArrayList<>();

    public DictionaryEntryDto() {
    }

    public DictionaryEntryDto(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    public List<String> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<String> definitions) {
        this.definitions = definitions == null ? new ArrayList<>() : definitions;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples == null ? new ArrayList<>() : examples;
    }
}
