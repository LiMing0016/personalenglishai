package com.personalenglishai.backend.service.vocabulary;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class VocabularyTermNormalizer {

    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern WRAPPING = Pattern.compile("^[\\p{Punct}\\p{Ps}\\p{Pe}‘’“”]+|[\\p{Punct}\\p{Ps}\\p{Pe}‘’“”]+$");
    private static final Pattern ENGLISH_TERM = Pattern.compile("[a-z]+(?:[ '-][a-z]+)*");

    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace("·", "").replace("•", "").replace("\u00AD", "")
                .trim().toLowerCase(Locale.ROOT);
        value = WRAPPING.matcher(value).replaceAll("").trim();
        return SPACE.matcher(value).replaceAll(" ");
    }

    public boolean isReviewRequired(String raw, String normalized) {
        return raw == null || raw.length() > 120 || normalized.isBlank()
                || !ENGLISH_TERM.matcher(normalized).matches();
    }
}
