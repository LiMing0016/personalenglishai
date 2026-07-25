package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VocabularyImportFingerprintTest {

    @Test
    void normalizes_line_endings_and_trims_text() {
        assertEquals(
                VocabularyImportFingerprint.calculate("  one\r\ntwo  ", null),
                VocabularyImportFingerprint.calculate("one\ntwo", null));
    }

    @Test
    void separates_text_from_raw_image_bytes_and_returns_lowercase_sha256() {
        String withoutImage = VocabularyImportFingerprint.calculate("one", null);
        String withZeroImageByte = VocabularyImportFingerprint.calculate("one", new byte[] {0});
        String withImage = VocabularyImportFingerprint.calculate(
                "one",
                "image".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(withoutImage, withZeroImageByte);
        assertNotEquals(withoutImage, withImage);
        assertTrue(withImage.matches("[0-9a-f]{64}"));
    }
}
