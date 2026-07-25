package com.personalenglishai.backend.service.vocabulary;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class VocabularyGenerationDeadline {

    private final long deadlineNanos;

    private VocabularyGenerationDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    static VocabularyGenerationDeadline fromNow(
            long leaseMs,
            long reserveMs,
            LongSupplier nanoTime) {
        long usableMs = Math.max(0L, leaseMs - Math.max(0L, reserveMs));
        long boundedMs = Math.min(usableMs, Long.MAX_VALUE / 1_000_000L);
        long now = nanoTime.getAsLong();
        long delta = TimeUnit.MILLISECONDS.toNanos(boundedMs);
        long deadline = now > Long.MAX_VALUE - delta ? Long.MAX_VALUE : now + delta;
        return new VocabularyGenerationDeadline(deadline);
    }

    int remainingBudgetMs(LongSupplier nanoTime, int maximumBudgetMs) {
        long remainingNanos = deadlineNanos - nanoTime.getAsLong();
        long remainingMs = remainingNanos / 1_000_000L;
        if (remainingNanos <= 0L || remainingMs <= 0L) {
            throw new VocabularyGenerationException(
                    "GENERATION_TIMEOUT", true, "Vocabulary generation attempt budget is exhausted");
        }
        return (int) Math.min(remainingMs, maximumBudgetMs);
    }
}
