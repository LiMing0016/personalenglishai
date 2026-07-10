package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VocabularyTermNormalizerTest {

    private final VocabularyTermNormalizer normalizer = new VocabularyTermNormalizer();

    @Test
    void normalizesDictionaryAndPastedForms() {
        assertAll(
                () -> assertEquals("innovative", normalizer.normalize("  (In·nova\u00ADtive). ")),
                () -> assertEquals("state-of-the-art", normalizer.normalize("STATE-OF-THE-ART")),
                () -> assertEquals("don't", normalizer.normalize("‘Don't’")),
                () -> assertEquals("machine learning", normalizer.normalize("machine   learning"))
        );
    }

    @Test
    void routesLongOrNonEnglishInputToReview() {
        assertTrue(normalizer.isReviewRequired("x".repeat(121), "x".repeat(121)));
        assertTrue(normalizer.isReviewRequired("你好", "你好"));
        assertFalse(normalizer.isReviewRequired("sustainable", "sustainable"));
    }
}
