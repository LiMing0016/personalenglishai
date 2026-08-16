package com.personalenglishai.backend.service.subscription;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageProductClassifierTest {
    private final AiUsageProductClassifier classifier = new AiUsageProductClassifier();

    @Test
    void classifiesStableProductDimensions() {
        assertThat(classifier.classify("assistant.conversation")).isEqualTo("assistant");
        assertThat(classifier.classify("ai.command.free_chat")).isEqualTo("assistant");
        assertThat(classifier.classify("writing.translate")).isEqualTo("translation");
        assertThat(classifier.classify("translation.feedback")).isEqualTo("translation");
        assertThat(classifier.classify("writing.evaluate")).isEqualTo("writing");
        assertThat(classifier.classify("vocabulary.card-generation")).isEqualTo("vocabulary");
    }

    @Test
    void unknownAndBlankFeaturesRemainVisibleAsOther() {
        assertThat(classifier.classify("future.unknown")).isEqualTo("other");
        assertThat(classifier.classify("")).isEqualTo("other");
        assertThat(classifier.classify(null)).isEqualTo("other");
    }
}
