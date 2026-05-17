package com.personalenglishai.backend.service.learning;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LearningLocalCandidateExtractor {
    private static final int MAX_WORDS = 50;
    private static final int MAX_PHRASES = 30;
    private static final int MAX_SENTENCES = 20;
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z'-]{2,}");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]{3,120})\\*\\*");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]{3,120})`");
    private static final Pattern PATTERN_LINE = Pattern.compile("(?i)([a-z][a-z\\s'-]{1,60}\\s\\+\\s[^\\r\\n]{2,120})");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "that", "this", "with", "from", "have", "has", "had", "was", "were",
            "are", "you", "your", "they", "their", "there", "then", "than", "but", "not", "can",
            "could", "would", "should", "will", "about", "into", "onto", "over", "under", "more",
            "some", "such", "very", "also", "when", "what", "which", "who", "how", "why", "its",
            "it's", "don't", "does", "did", "been", "being", "because", "these", "those");

    public List<ExtractedCandidate> extract(String text) {
        String normalizedInput = text == null ? "" : text.trim();
        if (normalizedInput.isEmpty()) {
            return List.of();
        }

        Map<String, CandidateAccumulator> candidates = new LinkedHashMap<>();
        collectSentencePatterns(normalizedInput, candidates);
        collectPhrases(normalizedInput, candidates);
        collectSentences(normalizedInput, candidates);
        collectWords(normalizedInput, candidates);

        return candidates.values().stream()
                .map(CandidateAccumulator::toCandidate)
                .sorted(Comparator.comparing(ExtractedCandidate::score).reversed()
                        .thenComparing(ExtractedCandidate::type)
                        .thenComparing(ExtractedCandidate::text))
                .limit(100)
                .toList();
    }

    private void collectSentencePatterns(String text, Map<String, CandidateAccumulator> candidates) {
        Matcher matcher = PATTERN_LINE.matcher(text);
        while (matcher.find()) {
            add(candidates, "sentence_pattern", matcher.group(1), "template_marker", 78);
        }
        for (String line : text.split("\\R")) {
            String cleaned = stripListMarker(line).trim();
            if (cleaned.contains("____") || cleaned.contains("_____") || cleaned.contains(" + ")) {
                add(candidates, "sentence_pattern", cleaned, "pattern_line", 72);
            }
        }
    }

    private void collectPhrases(String text, Map<String, CandidateAccumulator> candidates) {
        int count = 0;
        Matcher boldMatcher = BOLD_PATTERN.matcher(text);
        while (boldMatcher.find() && count < MAX_PHRASES) {
            if (addPhrase(candidates, boldMatcher.group(1), "markdown_bold")) {
                count++;
            }
        }

        Matcher codeMatcher = INLINE_CODE_PATTERN.matcher(text);
        while (codeMatcher.find() && count < MAX_PHRASES) {
            if (addPhrase(candidates, codeMatcher.group(1), "inline_code")) {
                count++;
            }
        }

        for (String line : text.split("\\R")) {
            if (count >= MAX_PHRASES) {
                break;
            }
            String trimmed = line.trim();
            if (!(trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•"))) {
                continue;
            }
            if (addPhrase(candidates, stripListMarker(trimmed), "list_item")) {
                count++;
            }
        }
    }

    private boolean addPhrase(Map<String, CandidateAccumulator> candidates, String value, String signal) {
        String cleaned = cleanCandidate(value);
        int words = countWords(cleaned);
        if (words < 2 || words > 8 || cleaned.length() > 120 || !containsEnglish(cleaned)) {
            return false;
        }
        add(candidates, "phrase", cleaned, signal, 64 + Math.min(words * 2, 12));
        return true;
    }

    private void collectSentences(String text, Map<String, CandidateAccumulator> candidates) {
        String prose = text.replaceAll("```[\\s\\S]*?```", " ");
        int count = 0;
        for (String sentence : prose.split("(?<=[.!?])\\s+|\\R+")) {
            if (count >= MAX_SENTENCES) {
                break;
            }
            String cleaned = cleanCandidate(stripListMarker(sentence));
            int words = countWords(cleaned);
            if (words < 5 || words > 35 || cleaned.length() > 260 || !containsEnglish(cleaned)) {
                continue;
            }
            add(candidates, "sentence", cleaned, "sentence_boundary", 55 + Math.min(words, 20));
            count++;
        }
    }

    private void collectWords(String text, Map<String, CandidateAccumulator> candidates) {
        Map<String, WordStat> words = new LinkedHashMap<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            String normalized = normalize(word);
            if (normalized.length() < 4 || STOP_WORDS.contains(normalized)) {
                continue;
            }
            words.computeIfAbsent(normalized, ignored -> new WordStat(word)).increment();
        }

        words.values().stream()
                .sorted(Comparator.comparing(WordStat::score).reversed().thenComparing(stat -> stat.display.toLowerCase(Locale.ROOT)))
                .limit(MAX_WORDS)
                .forEach(stat -> add(candidates, "word", stat.display, "word_frequency", stat.score()));
    }

    private void add(Map<String, CandidateAccumulator> candidates, String type, String text, String signal, int score) {
        String cleaned = cleanCandidate(text);
        String normalized = normalize(cleaned);
        if (cleaned.isBlank() || normalized.isBlank()) {
            return;
        }
        String key = type + ":" + normalized;
        candidates.computeIfAbsent(key, ignored -> new CandidateAccumulator(type, cleaned, normalized))
                .addSignal(signal, score);
    }

    private String stripListMarker(String line) {
        return line == null ? "" : line.replaceFirst("^\\s*[-*•]\\s+", "");
    }

    private String cleanCandidate(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replaceAll("^```[a-zA-Z]*", "")
                .replace("```", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                .trim();
    }

    private String normalize(String value) {
        return cleanCandidate(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9'\\s+-]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsEnglish(String value) {
        return value != null && value.matches(".*[A-Za-z].*");
    }

    private int countWords(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        Matcher matcher = WORD_PATTERN.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public record ExtractedCandidate(
            String type,
            String text,
            String normalizedText,
            int occurrenceCount,
            BigDecimal score,
            String signalsJson) {
    }

    private static final class CandidateAccumulator {
        private final String type;
        private final String text;
        private final String normalizedText;
        private final List<String> signals = new ArrayList<>();
        private int occurrenceCount;
        private int maxScore;

        private CandidateAccumulator(String type, String text, String normalizedText) {
            this.type = type;
            this.text = text;
            this.normalizedText = normalizedText;
        }

        private void addSignal(String signal, int score) {
            signals.add(signal);
            occurrenceCount++;
            maxScore = Math.max(maxScore, score + Math.min((occurrenceCount - 1) * 5, 20));
        }

        private ExtractedCandidate toCandidate() {
            return new ExtractedCandidate(
                    type,
                    text,
                    normalizedText.length() > 255 ? normalizedText.substring(0, 255) : normalizedText,
                    occurrenceCount,
                    BigDecimal.valueOf(maxScore),
                    "{\"signals\":" + signals.stream()
                            .map(signal -> "\"" + signal + "\"")
                            .toList() + "}");
        }
    }

    private static final class WordStat {
        private final String display;
        private int count;

        private WordStat(String display) {
            this.display = display;
        }

        private void increment() {
            count++;
        }

        private int score() {
            return 38 + Math.min(count * 8, 24) + Math.min(display.length(), 14);
        }
    }
}
