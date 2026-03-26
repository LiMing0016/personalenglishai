package com.personalenglishai.backend.ai.client;

public record OpenAiResponsesTextResult(
        String responseId,
        String outputText,
        Integer inputTokens,
        Integer cachedTokens,
        Integer payloadBytes
) {
}
