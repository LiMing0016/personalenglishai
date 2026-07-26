package com.personalenglishai.backend.service.subscription.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AiUsageActivityResponse(
        String metric,
        String unit,
        String timezone,
        LocalDate from,
        LocalDate to,
        long total,
        List<DayBucket> buckets) {

    public record DayBucket(
            LocalDate date,
            long total,
            Map<String, Long> byProduct) {
    }
}
