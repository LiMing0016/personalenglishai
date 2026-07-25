package com.personalenglishai.backend.service.vocabulary;

public interface VocabularyGenerationProvider {

    String key();

    GeneratedVocabularyCard generate(VocabularyGenerationInput input);
}
