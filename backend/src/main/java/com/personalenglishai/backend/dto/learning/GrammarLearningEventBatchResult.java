package com.personalenglishai.backend.dto.learning;

import java.util.List;

public record GrammarLearningEventBatchResult(
        boolean success,
        int acceptedCount,
        int deduplicatedCount,
        int rejectedCount,
        List<EventResult> results
) {
    public record EventResult(String eventId, String status) {
    }
}
