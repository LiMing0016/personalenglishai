package com.personalenglishai.backend.service.subscription;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.subscription.AiTokenUsageEvent;
import com.personalenglishai.backend.mapper.subscription.AiTokenUsageMapper;
import com.personalenglishai.backend.service.subscription.dto.AiUsageActivityResponse;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AiUsageActivityService {
    static final String METRIC_AI_TOKENS = "ai_tokens";
    static final String GRANULARITY_DAY = "day";
    static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final int MAX_RANGE_DAYS = 366;
    private static final List<String> PRODUCT_KEYS =
            List.of("assistant", "writing", "translation", "vocabulary", "other");

    private final AiTokenUsageMapper usageMapper;
    private final AiUsageProductClassifier productClassifier;

    public AiUsageActivityService(
            AiTokenUsageMapper usageMapper,
            AiUsageProductClassifier productClassifier) {
        this.usageMapper = usageMapper;
        this.productClassifier = productClassifier;
    }

    public AiUsageActivityResponse getActivity(
            Long userId,
            String metric,
            String granularity,
            LocalDate from,
            LocalDate to,
            String timezone) {
        if (!METRIC_AI_TOKENS.equals(metric)) {
            throw validation("当前仅支持 AI Token 用量");
        }
        if (!GRANULARITY_DAY.equals(granularity)) {
            throw validation("当前仅支持按日查询");
        }

        ZoneId zone = resolveZone(timezone);
        LocalDate resolvedTo = to == null ? LocalDate.now(zone) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(MAX_RANGE_DAYS - 1L) : from;
        validateRange(resolvedFrom, resolvedTo);

        LocalDateTime fromUtc = LocalDateTime.ofInstant(
                resolvedFrom.atStartOfDay(zone).toInstant(),
                ZoneOffset.UTC);
        LocalDateTime toUtcExclusive = LocalDateTime.ofInstant(
                resolvedTo.plusDays(1).atStartOfDay(zone).toInstant(),
                ZoneOffset.UTC);
        List<AiTokenUsageEvent> events =
                usageMapper.selectEventsByUserAndOccurredAt(userId, fromUtc, toUtcExclusive);
        Map<LocalDate, BucketAccumulator> buckets = new TreeMap<>();
        long total = 0L;

        for (AiTokenUsageEvent event : events == null ? List.<AiTokenUsageEvent>of() : events) {
            long tokens = nonNegative(event.getTotalTokens());
            if (tokens == 0L || event.getOccurredAt() == null) {
                continue;
            }
            LocalDate date = event.getOccurredAt()
                    .toInstant(ZoneOffset.UTC)
                    .atZone(zone)
                    .toLocalDate();
            if (date.isBefore(resolvedFrom) || date.isAfter(resolvedTo)) {
                continue;
            }
            String product = productClassifier.classify(event.getFeatureKey());
            BucketAccumulator bucket = buckets.computeIfAbsent(date, ignored -> new BucketAccumulator());
            bucket.add(product, tokens);
            total += tokens;
        }

        List<AiUsageActivityResponse.DayBucket> responseBuckets = new ArrayList<>();
        buckets.forEach((date, bucket) -> responseBuckets.add(
                new AiUsageActivityResponse.DayBucket(date, bucket.total, Map.copyOf(bucket.byProduct))));
        return new AiUsageActivityResponse(
                METRIC_AI_TOKENS,
                "token",
                zone.getId(),
                resolvedFrom,
                resolvedTo,
                total,
                List.copyOf(responseBuckets));
    }

    private static ZoneId resolveZone(String timezone) {
        String candidate = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim();
        try {
            return ZoneId.of(candidate);
        } catch (DateTimeException exception) {
            throw validation("无效的时区");
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw validation("开始日期不能晚于结束日期");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1L;
        if (days > MAX_RANGE_DAYS) {
            throw validation("用量查询最多支持 366 天");
        }
    }

    private static long nonNegative(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private static BizException validation(String message) {
        return new BizException(ErrorCode.COMMON_VALIDATION_ERROR, message);
    }

    private static final class BucketAccumulator {
        private final Map<String, Long> byProduct = new LinkedHashMap<>();
        private long total;

        private BucketAccumulator() {
            PRODUCT_KEYS.forEach(key -> byProduct.put(key, 0L));
        }

        private void add(String product, long tokens) {
            byProduct.compute(product, (key, current) -> (current == null ? 0L : current) + tokens);
            total += tokens;
        }
    }
}
