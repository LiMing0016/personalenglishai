package com.personalenglishai.backend.service.writing.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 评分计算工具类（纯函数，无外部依赖，便于单元测试）
 */
public final class WritingScoreUtils {

    private WritingScoreUtils() {}

    // ── 高考换算 ────────────────────────────────────────────────────────────

    public static int computeGaokaoRaw(int averageScore, String mode) {
        int maxScore = "exam".equals(mode) ? 25 : 15;
        return (int) Math.round(averageScore / 100.0 * maxScore);
    }

    public static String computeGaokaoband(int gaokaoScore, String mode) {
        int maxScore = "exam".equals(mode) ? 25 : 15;
        double ratio = (double) gaokaoScore / maxScore;
        if (ratio >= 0.87) return "优秀";
        if (ratio >= 0.67) return "良好";
        if (ratio >= 0.47) return "中等";
        if (ratio >= 0.27) return "偏低";
        return "需要提高";
    }

    // ── 等级标准化 ──────────────────────────────────────────────────────────

    public static String normalizeLevel(String level) {
        String n = level == null ? "" : level.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (n) {
            case "A", "B", "C", "D", "E" -> n;
            default -> "C";
        };
    }

    // ── 词数统计 ────────────────────────────────────────────────────────────

    public static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    // ── 句子数统计 ────────────────────────────────────────────────────────────

    public static int countSentences(String text) {
        if (text == null || text.isBlank()) return 0;
        // Split on sentence-ending punctuation followed by space or end-of-string
        String[] parts = text.trim().split("[.!?]+(?:\\s|$)");
        int count = 0;
        for (String p : parts) {
            if (!p.isBlank()) count++;
        }
        return Math.max(count, 1);
    }

    // ── 段落数统计 ────────────────────────────────────────────────────────────

    public static int countParagraphs(String text) {
        if (text == null || text.isBlank()) return 0;
        // Split on blank lines or double newlines
        String[] parts = text.trim().split("\\n\\s*\\n");
        int count = 0;
        for (String p : parts) {
            if (!p.isBlank()) count++;
        }
        return Math.max(count, 1);
    }

    // ── 指数加权平均 ────────────────────────────────────────────────────────

    /**
     * EWA：旧值 70% + 新值 30%。首次评分（old=null）直接返回新值。
     */
    public static BigDecimal ewa(BigDecimal old, Integer newScore) {
        if (newScore == null) return old == null ? BigDecimal.valueOf(60) : old;
        BigDecimal current = BigDecimal.valueOf(newScore);
        if (old == null) return current.setScale(2, RoundingMode.HALF_UP);
        return old.multiply(BigDecimal.valueOf(0.7))
                .add(current.multiply(BigDecimal.valueOf(0.3)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ── 进步计算 ────────────────────────────────────────────────────────────

    public static String buildImprovementMessage(int delta) {
        if (delta >= 2)  return "本次比历史均分提高了 " + delta + " 分，进步明显，继续保持！";
        if (delta == 1)  return "本次比历史均分高 1 分，稍有进步，努力稳定发挥！";
        if (delta == 0)  return "本次与历史均分持平，继续努力，争取突破！";
        if (delta == -1) return "本次比历史均分低 1 分，发挥略有波动，保持练习！";
        return "本次比历史均分低 " + Math.abs(delta) + " 分，需加强薄弱环节的针对性练习。";
    }

}
