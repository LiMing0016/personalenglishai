package com.personalenglishai.backend.service.subscription;

public record AiTokenUsageRecord(
        String usageEventId,
        Long userId,
        String featureKey,
        String provider,
        String model,
        Long inputTokens,
        Long cachedInputTokens,
        Long outputTokens,
        Long reasoningTokens,
        Long totalTokens,
        String traceId
) {
}
