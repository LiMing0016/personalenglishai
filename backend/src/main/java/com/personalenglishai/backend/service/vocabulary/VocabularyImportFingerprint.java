package com.personalenglishai.backend.service.vocabulary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class VocabularyImportFingerprint {

    private VocabularyImportFingerprint() {
    }

    public static String calculate(String text, byte[] imageBytes) {
        String normalized = Objects.requireNonNullElse(text, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(normalized.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (imageBytes != null) {
                digest.update(imageBytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
