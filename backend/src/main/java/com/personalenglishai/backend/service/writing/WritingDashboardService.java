package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.mapper.DocumentScoreSummaryMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WritingDashboardService {

    private final DocumentScoreSummaryMapper documentScoreSummaryMapper;

    public WritingDashboardService(DocumentScoreSummaryMapper documentScoreSummaryMapper) {
        this.documentScoreSummaryMapper = documentScoreSummaryMapper;
    }

    public Map<String, Object> buildAssetDashboard(Long userId, String mode, String granularity) {
        String normalizedMode = normalizeMode(mode);
        String normalizedGranularity = normalizeGranularity(granularity);
        List<Map<String, Object>> rows =
                documentScoreSummaryMapper.selectDashboardAssetRowsByUserIdAndMode(userId, normalizedMode);

        long totalEssays = rows.size();
        long totalWords = 0L;
        long totalSentences = 0L;
        long totalGrammarErrors = 0L;
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            int latestWordCount = readInt(row.get("latestWordCount"));
            int latestSentenceCount = readInt(row.get("latestSentenceCount"));
            int latestGrammarErrorCount = readInt(row.get("latestGrammarErrorCount"));
            LocalDateTime latestEvaluationAt = readDateTime(row.get("latestEvaluationAt"));
            if (latestEvaluationAt == null) {
                continue;
            }

            totalWords += latestWordCount;
            totalSentences += latestSentenceCount;
            totalGrammarErrors += latestGrammarErrorCount;

            LocalDate periodStart = resolvePeriodStart(latestEvaluationAt.toLocalDate(), normalizedGranularity);
            String periodKey = periodStart.toString();
            Bucket bucket = buckets.computeIfAbsent(
                    periodKey,
                    ignored -> new Bucket(periodStart, formatPeriodLabel(periodStart, normalizedGranularity))
            );
            bucket.wordCount += latestWordCount;
            bucket.sentenceCount += latestSentenceCount;
            bucket.essayCount += 1;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEssays", totalEssays);
        summary.put("totalWords", totalWords);
        summary.put("totalSentences", totalSentences);
        summary.put("avgGrammarErrorsPerEssay", totalEssays == 0
                ? 0.0
                : Math.round((totalGrammarErrors * 10.0) / totalEssays) / 10.0);

        List<Map<String, Object>> series = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("periodStart", bucket.periodStart.toString());
            item.put("periodLabel", bucket.periodLabel);
            item.put("wordCount", bucket.wordCount);
            item.put("sentenceCount", bucket.sentenceCount);
            item.put("essayCount", bucket.essayCount);
            series.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("series", series);
        return response;
    }

    private String normalizeMode(String mode) {
        if (mode == null) {
            return "all";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if ("free".equals(normalized) || "exam".equals(normalized)) {
            return normalized;
        }
        return "all";
    }

    private String normalizeGranularity(String granularity) {
        if (granularity == null) {
            return "month";
        }
        return "week".equalsIgnoreCase(granularity.trim()) ? "week" : "month";
    }

    private LocalDate resolvePeriodStart(LocalDate date, String granularity) {
        if ("week".equals(granularity)) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return date.withDayOfMonth(1);
    }

    private String formatPeriodLabel(LocalDate periodStart, String granularity) {
        if ("week".equals(granularity)) {
            return periodStart.getMonthValue() + "/" + periodStart.getDayOfMonth();
        }
        return periodStart.getMonthValue() + "月";
    }

    private int readInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw && !raw.isBlank()) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private LocalDateTime readDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private static final class Bucket {
        private final LocalDate periodStart;
        private final String periodLabel;
        private long wordCount;
        private long sentenceCount;
        private long essayCount;

        private Bucket(LocalDate periodStart, String periodLabel) {
            this.periodStart = periodStart;
            this.periodLabel = periodLabel;
        }
    }
}
