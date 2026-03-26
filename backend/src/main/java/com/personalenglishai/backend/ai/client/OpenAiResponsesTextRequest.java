package com.personalenglishai.backend.ai.client;

public record OpenAiResponsesTextRequest(
        String model,
        String instructions,
        String input,
        String previousResponseId,
        String promptCacheKey,
        String promptCacheRetention,
        boolean store,
        Integer maxOutputTokens
) {
}
