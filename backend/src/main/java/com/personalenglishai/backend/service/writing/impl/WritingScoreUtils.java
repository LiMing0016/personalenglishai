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

    // ── 题目有效性检测 ───────────────────────────────────────────────────────

    /**
     * 检测 taskPrompt 是否为有效写作题目（非乱码）。
     * 规则：有效字符（字母+汉字）占比 >= 35%，且长度 >= 8。
     */
    public static boolean isValidTaskPrompt(String prompt) {
        if (prompt == null || prompt.trim().length() < 8) return false;
        String text = prompt.trim();
        // A valid prompt must have either spaces (multiple words) or CJK characters.
        // A single unspaced token like "128045325adfafbfd" is always gibberish.
        boolean hasCjk = text.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        boolean hasSpace = text.contains(" ");
        if (!hasCjk && !hasSpace) return false;
        long validChars = text.chars()
                .filter(Character::isLetter)
                .count();
        return (double) validChars / text.length() >= 0.35;
    }

    /** 从题目文本中提取词数要求（如"120词"→120），找不到返回 null */
    public static Integer extractWordCount(String prompt) {
        if (prompt == null) return null;
        var matcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*(词|字|words?)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(prompt);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}
