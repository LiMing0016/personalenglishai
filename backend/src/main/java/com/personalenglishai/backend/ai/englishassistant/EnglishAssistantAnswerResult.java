package com.personalenglishai.backend.ai.englishassistant;

public record EnglishAssistantAnswerResult(
        String responseId,
        String message,
        Integer inputTokens,
        Integer cachedTokens
) {
}
