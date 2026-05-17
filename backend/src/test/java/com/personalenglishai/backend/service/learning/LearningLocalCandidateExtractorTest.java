package com.personalenglishai.backend.service.learning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningLocalCandidateExtractorTest {

    private final LearningLocalCandidateExtractor extractor = new LearningLocalCandidateExtractor();

    @Test
    void extractCollectsWordsPhrasesSentencesAndPatterns() {
        List<LearningLocalCandidateExtractor.ExtractedCandidate> candidates = extractor.extract("""
                更自然的同类表达
                - help improve people's perceptions of Chinese manufacturing
                - contribute to a more positive global perception of Chinese manufacturing

                你可以这样记
                help + 动词原形 + perceptions of + 名词

                Chinese technology exports have helped improve global perceptions of Chinese manufacturing.
                """);

        assertThat(candidates)
                .extracting(LearningLocalCandidateExtractor.ExtractedCandidate::type)
                .contains("word", "phrase", "sentence", "sentence_pattern");
        assertThat(candidates)
                .anySatisfy(candidate -> assertThat(candidate.normalizedText())
                        .contains("help improve people's perceptions"));
        assertThat(candidates)
                .anySatisfy(candidate -> assertThat(candidate.normalizedText())
                        .contains("chinese technology exports"));
    }
}
