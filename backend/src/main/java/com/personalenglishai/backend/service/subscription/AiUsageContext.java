package com.personalenglishai.backend.service.subscription;

public record AiUsageContext(
        Long userId,
        String featureKey,
        String traceId
) {
}
