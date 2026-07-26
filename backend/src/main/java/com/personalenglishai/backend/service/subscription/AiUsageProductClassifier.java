package com.personalenglishai.backend.service.subscription;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AiUsageProductClassifier {

    public String classify(String featureKey) {
        String key = featureKey == null ? "" : featureKey.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("assistant.") || key.startsWith("ai.command.")) {
            return "assistant";
        }
        if ("writing.translate".equals(key) || key.startsWith("translation.")) {
            return "translation";
        }
        if (key.startsWith("writing.")) {
            return "writing";
        }
        if (key.startsWith("vocabulary.")) {
            return "vocabulary";
        }
        return "other";
    }
}
