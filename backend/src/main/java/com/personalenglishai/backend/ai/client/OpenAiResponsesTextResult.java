package com.personalenglishai.backend.ai.client;

public record OpenAiResponsesTextResult(
        String responseId,
        String outputText,
        Integer inputTokens,
        Integer cachedTokens,
        Integer outputTokens,
        Integer reasoningTokens,
        Integer totalTokens,
        Integer payloadBytes
) {
    public OpenAiResponsesTextResult(String responseId,
                                     String outputText,
                                     Integer inputTokens,
                                     Integer cachedTokens,
                                     Integer payloadBytes) {
        this(responseId, outputText, inputTokens, cachedTokens, null, null, null, payloadBytes);
    }
}
