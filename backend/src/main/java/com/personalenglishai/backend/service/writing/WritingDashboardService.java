package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.mapper.DocumentScoreSummaryMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class WritingDashboardService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentScoreSummaryMapper documentScoreSummaryMapper;
    private final EssayEvaluationMapper essayEvaluationMapper;

    public WritingDashboardService(
            DocumentScoreSummaryMapper documentScoreSummaryMapper,
            EssayEvaluationMapper essayEvaluationMapper) {
        this.documentScoreSummaryMapper = documentScoreSummaryMapper;
        this.essayEvaluationMapper = essayEvaluationMapper;
    }

    public Map<String, Object> buildDashboard(Long userId, String range, String mode, String start, String end) {
        Scope scope = normalizeScope(range, mode, start, end);
        List<Map<String, Object>> latestRows = documentScoreSummaryMapper.selectDashboardLatestRows(
                userId, scope.mode(), scope.startAt(), scope.endExclusive());
        List<Map<String, Object>> submissionRows = essayEvaluationMapper.selectDashboardSubmissionRows(
                userId, scope.mode(), scope.startAt(), scope.endExclusive());

        String granularity = resolveGranularity(scope, latestRows, submissionRows);
        Map<String, Object> overview = buildOverview(latestRows, submissionRows, granularity);
        Map<String, Object> growth = buildGrowth(latestRows, submissionRows);

        Map<String, Object> scopeMap = new LinkedHashMap<>();
        scopeMap.put("range", scope.range());
        scopeMap.put("mode", scope.mode());
        scopeMap.put("scorePolicy", "latest");
        scopeMap.put("start", scope.startDate() == null ? "" : scope.startDate().toString());
        scopeMap.put("end", scope.endDate() == null ? "" : scope.endDate().toString());
        scopeMap.put("granularity", granularity);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scope", scopeMap);
        response.put("overview", overview);
        response.put("growth", growth);
        return response;
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
        LinkedHashMap<String, AssetBucket> buckets = new LinkedHashMap<>();

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
            AssetBucket bucket = buckets.computeIfAbsent(
                    periodKey,
                    ignored -> new AssetBucket(periodStart, formatPeriodLabel(periodStart, normalizedGranularity))
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
        for (AssetBucket bucket : buckets.values()) {
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

    private Map<String, Object> buildOverview(
            List<Map<String, Object>> latestRows,
            List<Map<String, Object>> submissionRows,
            String granularity) {
        List<Integer> latestScores = latestRows.stream()
                .map(row -> readInt(row.get("latestOverallScore")))
                .filter(score -> score > 0)
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEssays", latestScores.size());
        summary.put("totalSubmissions", submissionRows.size());
        summary.put("averageScore", averageScore(latestScores));
        summary.put("bestScore", latestScores.stream().mapToInt(Integer::intValue).max().orElse(0));

        LinkedHashMap<String, TrendBucket> buckets = new LinkedHashMap<>();
        for (Map<String, Object> row : latestRows) {
            LocalDateTime at = readDateTime(row.get("latestEvaluationAt"));
            int score = readInt(row.get("latestOverallScore"));
            if (at == null || score <= 0) continue;
            TrendBucket bucket = buckets.computeIfAbsent(bucketKey(at.toLocalDate(), granularity), key ->
                    new TrendBucket(key, bucketLabel(at.toLocalDate(), granularity)));
            bucket.essayCount += 1;
            bucket.scores.add(score);
        }
        for (Map<String, Object> row : submissionRows) {
            LocalDateTime at = readDateTime(row.get("evaluatedAt"));
            if (at == null) continue;
            TrendBucket bucket = buckets.computeIfAbsent(bucketKey(at.toLocalDate(), granularity), key ->
                    new TrendBucket(key, bucketLabel(at.toLocalDate(), granularity)));
            bucket.submissionCount += 1;
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (TrendBucket bucket : buckets.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", bucket.key);
            item.put("sourceLabel", bucket.label);
            item.put("essayCount", bucket.essayCount);
            item.put("submissionCount", bucket.submissionCount);
            item.put("averageScore", averageScore(bucket.scores));
            item.put("bestScore", bucket.scores.stream().mapToInt(Integer::intValue).max().orElse(0));
            trend.add(item);
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("summary", summary);
        overview.put("trend", trend);
        overview.put("insight", buildInsight(latestScores, submissionRows.size(), highScorePercent(latestScores)));
        return overview;
    }

    private Map<String, Object> buildGrowth(List<Map<String, Object>> latestRows, List<Map<String, Object>> submissionRows) {
        List<Map<String, Object>> scoreTrend = new ArrayList<>();
        Integer previous = null;
        int essayNo = 1;
        for (Map<String, Object> row : latestRows) {
            int score = readInt(row.get("latestOverallScore"));
            LocalDateTime at = readDateTime(row.get("latestEvaluationAt"));
            if (score <= 0 || at == null) continue;
            int delta = previous == null ? 0 : score - previous;
            previous = score;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("essayNo", essayNo++);
            item.put("title", readString(row.get("title"), "未命名作文"));
            item.put("mode", readString(row.get("mode"), "free"));
            item.put("score", score);
            item.put("scoredAt", DATE_TIME.format(at));
            item.put("delta", delta);
            item.put("aiSuggestion", delta < 0 ? "这篇得分较上一篇回落，建议复盘结构和基础错误。" : "继续保持当前练习节奏。");
            scoreTrend.add(item);
        }

        List<Integer> scores = latestRows.stream()
                .map(row -> readInt(row.get("latestOverallScore")))
                .filter(score -> score > 0)
                .toList();
        List<Map<String, Object>> distribution = buildDistribution(scores);
        List<Map<String, Object>> scoreBands = buildScoreBands();
        List<Map<String, Object>> scatter = buildScatter(scoreTrend, distribution);

        Map<String, Object> monthlyGoal = buildMonthlyGoal(latestRows);
        Map<String, Object> streak = buildStreak(latestRows);
        int highScorePercent = highScorePercent(scores);

        Map<String, Object> growth = new LinkedHashMap<>();
        growth.put("essayScoreTrend", scoreTrend);
        growth.put("scoreDistribution", distribution);
        growth.put("scoreBands", scoreBands);
        growth.put("highScorePercent", highScorePercent);
        growth.put("scoreScatter", scatter);
        growth.put("monthlyGoal", monthlyGoal);
        growth.put("streak", streak);
        growth.put("insight", buildInsight(scores, submissionRows.size(), highScorePercent));
        return growth;
    }

    private Scope normalizeScope(String range, String mode, String start, String end) {
        String normalizedRange = normalizeRange(range);
        String normalizedMode = normalizeMode(mode);
        LocalDate today = LocalDate.now();
        LocalDate startDate = null;
        LocalDate endDate = null;

        if ("custom".equals(normalizedRange)) {
            startDate = parseDate(start);
            endDate = parseDate(end);
            if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
                normalizedRange = "30d";
            }
        }

        if (!"custom".equals(normalizedRange) && !"all".equals(normalizedRange)) {
            endDate = today;
            int days = switch (normalizedRange) {
                case "7d" -> 7;
                case "14d" -> 14;
                case "year" -> 365;
                default -> 30;
            };
            startDate = today.minusDays(days - 1L);
        }

        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        return new Scope(normalizedRange, normalizedMode, startDate, endDate, startAt, endExclusive);
    }

    private String resolveGranularity(Scope scope, List<Map<String, Object>> latestRows, List<Map<String, Object>> submissionRows) {
        if ("7d".equals(scope.range()) || "14d".equals(scope.range())) return "day";
        if ("30d".equals(scope.range()) || "custom".equals(scope.range())) return "week";
        if ("year".equals(scope.range())) return "month";

        LocalDate first = null;
        LocalDate last = null;
        for (Map<String, Object> row : latestRows) {
            LocalDateTime at = readDateTime(row.get("latestEvaluationAt"));
            if (at == null) continue;
            first = first == null || at.toLocalDate().isBefore(first) ? at.toLocalDate() : first;
            last = last == null || at.toLocalDate().isAfter(last) ? at.toLocalDate() : last;
        }
        for (Map<String, Object> row : submissionRows) {
            LocalDateTime at = readDateTime(row.get("evaluatedAt"));
            if (at == null) continue;
            first = first == null || at.toLocalDate().isBefore(first) ? at.toLocalDate() : first;
            last = last == null || at.toLocalDate().isAfter(last) ? at.toLocalDate() : last;
        }
        if (first == null || last == null) return "month";
        return ChronoUnit.DAYS.between(first, last) > 730 ? "year" : "month";
    }

    private String normalizeRange(String range) {
        if (range == null) return "30d";
        String value = range.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "7d", "14d", "30d", "year", "all", "custom" -> value;
            default -> "30d";
        };
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

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String bucketKey(LocalDate date, String granularity) {
        if ("year".equals(granularity)) return String.valueOf(date.getYear());
        if ("month".equals(granularity)) return date.withDayOfMonth(1).toString();
        if ("week".equals(granularity)) return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
        return date.toString();
    }

    private String bucketLabel(LocalDate date, String granularity) {
        if ("year".equals(granularity)) return date.getYear() + "年";
        if ("month".equals(granularity)) return date.getYear() + "年" + String.format("%02d", date.getMonthValue()) + "月";
        if ("week".equals(granularity)) {
            LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = start.plusDays(6);
            return start + " - " + end;
        }
        return date.toString();
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

    private int averageScore(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) return 0;
        return (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private List<Map<String, Object>> buildDistribution(List<Integer> scores) {
        List<Band> bands = bands();
        int total = scores.size();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Band band : bands) {
            long count = scores.stream().filter(score -> band.contains(score)).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", band.key);
            item.put("label", band.label);
            item.put("stage", band.stage);
            item.put("min", band.nullMin ? null : band.min);
            item.put("max", band.max);
            item.put("count", count);
            item.put("percent", total == 0 ? 0 : Math.round(count * 100.0 / total));
            item.put("color", band.color);
            item.put("backgroundColor", band.backgroundColor);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> buildScoreBands() {
        return bands().stream().map(band -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", band.key);
            item.put("label", band.stage);
            item.put("min", band.min);
            item.put("max", band.max);
            item.put("color", band.backgroundColor);
            return item;
        }).toList();
    }

    private List<Map<String, Object>> buildScatter(List<Map<String, Object>> scoreTrend, List<Map<String, Object>> distribution) {
        List<Map<String, Object>> scatter = new ArrayList<>();
        for (Map<String, Object> point : scoreTrend) {
            int score = readInt(point.get("score"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", formatMonth(readString(point.get("scoredAt"), "")));
            item.put("score", score);
            item.put("title", point.get("title"));
            item.put("mode", point.get("mode"));
            item.put("scoredAt", point.get("scoredAt"));
            item.put("bandLabel", resolveBandLabel(score, distribution));
            scatter.add(item);
        }
        return scatter;
    }

    private Map<String, Object> buildMonthlyGoal(List<Map<String, Object>> latestRows) {
        LocalDate today = LocalDate.now();
        long done = latestRows.stream()
                .map(row -> readDateTime(row.get("latestEvaluationAt")))
                .filter(at -> at != null && at.getYear() == today.getYear() && at.getMonth() == today.getMonth())
                .count();
        Map<String, Object> monthlyGoal = new LinkedHashMap<>();
        monthlyGoal.put("done", done);
        monthlyGoal.put("target", 3);
        monthlyGoal.put("remaining", Math.max(0, 3 - done));
        return monthlyGoal;
    }

    private Map<String, Object> buildStreak(List<Map<String, Object>> latestRows) {
        Set<LocalDate> activeDays = new LinkedHashSet<>();
        for (Map<String, Object> row : latestRows) {
            LocalDateTime at = readDateTime(row.get("latestEvaluationAt"));
            if (at != null) activeDays.add(at.toLocalDate());
        }
        int current = 0;
        LocalDate cursor = LocalDate.now();
        while (activeDays.contains(cursor)) {
            current += 1;
            cursor = cursor.minusDays(1);
        }
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : activeDays.stream().sorted().toList()) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            best = Math.max(best, run);
            previous = day;
        }
        Map<String, Object> streak = new LinkedHashMap<>();
        streak.put("currentDays", current);
        streak.put("bestDays", best);
        streak.put("activeDays", activeDays.size());
        return streak;
    }

    private String buildInsight(List<Integer> scores, int submissionCount, int highScorePercent) {
        if (scores.isEmpty()) return "先完成一篇作文评分后，这里会展示写作趋势和建议。";
        if (submissionCount < 3) return "评分样本仍偏少，建议完成 3 次以上评分后再观察趋势。";
        if (scores.size() >= 6) {
            double recent = scores.subList(scores.size() - 3, scores.size()).stream().mapToInt(Integer::intValue).average().orElse(0);
            double previous = scores.subList(scores.size() - 6, scores.size() - 3).stream().mapToInt(Integer::intValue).average().orElse(0);
            if (recent > previous) return "最近 3 篇平均分高于前 3 篇，建议保持当前练习节奏。";
            if (recent < previous) return "最近 3 篇平均分低于前 3 篇，建议复盘最近作文中的结构和基础错误。";
        }
        if (highScorePercent < 30) return "80 分以上占比仍偏低，建议优先稳定基础表达和段落结构。";
        return "当前数据较稳定，可以继续按固定频率练习并观察高分占比变化。";
    }

    private int highScorePercent(List<Integer> scores) {
        if (scores.isEmpty()) return 0;
        long high = scores.stream().filter(score -> score >= 80).count();
        return (int) Math.round(high * 100.0 / scores.size());
    }

    private String resolveBandLabel(int score, List<Map<String, Object>> distribution) {
        for (Map<String, Object> bucket : distribution) {
            Object minValue = bucket.get("min");
            int min = minValue instanceof Number n ? n.intValue() : 0;
            int max = readInt(bucket.get("max"));
            if (score >= min && score < max) {
                return readString(bucket.get("label"), "") + " " + readString(bucket.get("stage"), "");
            }
        }
        return "-";
    }

    private String formatMonth(String scoredAt) {
        if (scoredAt == null || scoredAt.length() < 7) return "未知";
        try {
            LocalDate date = LocalDate.parse(scoredAt.substring(0, 10));
            return date.getMonthValue() + "月";
        } catch (RuntimeException ignored) {
            return "未知";
        }
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

    private String readString(Object value, String fallback) {
        if (value == null) return fallback;
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? fallback : raw;
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

    private List<Band> bands() {
        return List.of(
                new Band("under-60", "<60", "需要补基础", 0, 60, true, "#D97A72", "#F7D8D4"),
                new Band("60-70", "60-70", "基础建立", 60, 70, false, "#D49A45", "#F3E0BD"),
                new Band("70-80", "70-80", "稳定提升", 70, 80, false, "#A7B45F", "#E7E8C8"),
                new Band("80-90", "80-90", "良好", 80, 90, false, "#63AE86", "#D7EADD"),
                new Band("90-100", "90-100", "优秀", 90, 101, false, "#6999C2", "#D8E6F2")
        );
    }

    private record Scope(
            String range,
            String mode,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime startAt,
            LocalDateTime endExclusive) {
    }

    private static final class TrendBucket {
        private final String key;
        private final String label;
        private long essayCount;
        private long submissionCount;
        private final List<Integer> scores = new ArrayList<>();

        private TrendBucket(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final class AssetBucket {
        private final LocalDate periodStart;
        private final String periodLabel;
        private long wordCount;
        private long sentenceCount;
        private long essayCount;

        private AssetBucket(LocalDate periodStart, String periodLabel) {
            this.periodStart = periodStart;
            this.periodLabel = periodLabel;
        }
    }

    private static final class Band {
        private final String key;
        private final String label;
        private final String stage;
        private final int min;
        private final int max;
        private final boolean nullMin;
        private final String color;
        private final String backgroundColor;

        private Band(String key, String label, String stage, int min, int max, boolean nullMin, String color, String backgroundColor) {
            this.key = key;
            this.label = label;
            this.stage = stage;
            this.min = min;
            this.max = max;
            this.nullMin = nullMin;
            this.color = color;
            this.backgroundColor = backgroundColor;
        }

        private boolean contains(int score) {
            return score >= min && score < max;
        }
    }
}
