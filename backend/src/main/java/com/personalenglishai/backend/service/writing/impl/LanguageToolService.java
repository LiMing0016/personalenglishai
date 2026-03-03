package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic grammar / spelling checker backed by LanguageTool.
 * <p>
 * JLanguageTool is <b>not thread-safe</b>, so we keep one instance per worker
 * thread via ThreadLocal with lazy initialisation.
 * <p>
 * Only objective, rule-based categories are enabled — subjective style rules
 * (STYLE, REDUNDANCY, etc.) are left to the AI scorer.
 */
@Service
public class LanguageToolService {

    private static final Logger log = LoggerFactory.getLogger(LanguageToolService.class);

    @Value("${languagetool.enabled:true}")
    private boolean enabled;

    // ── Categories to DISABLE (subjective / noisy) ──────────────────────
    private static final Set<String> DISABLED_CATEGORIES = Set.of(
            "STYLE", "REDUNDANCY", "COLLOQUIALISMS",
            "GENDER_NEUTRALITY", "PLAIN_ENGLISH", "CREATIVE_WRITING",
            "TEXT_ANALYSIS", "MISC"
    );

    // ── Individual rules to DISABLE (too noisy for ESL essays) ──────────
    private static final Set<String> DISABLED_RULES = Set.of(
            "WHITESPACE_RULE", "EN_QUOTES", "DASH_RULE",
            "COMMA_PARENTHESIS_WHITESPACE", "DOUBLE_PUNCTUATION",
            "UPPERCASE_SENTENCE_START", "EN_UNPAIRED_BRACKETS",
            "SENTENCE_WHITESPACE", "PARAGRAPH_REPEAT_BEGINNING_RULE"
    );

    // ThreadLocal — lazy-init per worker thread
    private final ThreadLocal<JLanguageTool> threadLocalTool = ThreadLocal.withInitial(() -> {
        JLanguageTool tool = new JLanguageTool(Languages.getLanguageForShortCode("en-US"));
        // Disable subjective categories
        tool.getCategories().forEach((catId, cat) -> {
            if (DISABLED_CATEGORIES.contains(catId.toString())) {
                tool.disableCategory(catId);
            }
        });
        // Disable noisy individual rules
        for (String ruleId : DISABLED_RULES) {
            tool.disableRule(ruleId);
        }
        return tool;
    });

    /**
     * Check the essay and return a list of ErrorDto.
     * Returns an empty list if disabled or on any internal error.
     */
    public List<WritingEvaluateResponse.ErrorDto> check(String essay) {
        if (!enabled || essay == null || essay.isBlank()) {
            return List.of();
        }
        try {
            long start = System.currentTimeMillis();
            List<RuleMatch> matches = threadLocalTool.get().check(essay);
            long elapsed = System.currentTimeMillis() - start;
            log.info("LanguageTool check done. matches={} elapsed={}ms", matches.size(), elapsed);

            List<WritingEvaluateResponse.ErrorDto> errors = new ArrayList<>();
            int idx = 1;
            for (RuleMatch match : matches) {
                WritingEvaluateResponse.ErrorDto dto = toErrorDto(match, essay, idx++);
                if (dto != null) {
                    errors.add(dto);
                }
            }
            return errors;
        } catch (Exception e) {
            log.warn("LanguageTool check failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Mapping helpers
    // ════════════════════════════════════════════════════════════════

    private WritingEvaluateResponse.ErrorDto toErrorDto(RuleMatch match, String essay, int idx) {
        int from = match.getFromPos();
        int to = match.getToPos();
        if (from < 0 || to <= from || to > essay.length()) return null;

        String original = essay.substring(from, to);
        if (original.isBlank()) return null;

        String suggestion = match.getSuggestedReplacements().isEmpty()
                ? ""
                : match.getSuggestedReplacements().get(0);

        String ruleId = match.getRule().getId().toUpperCase(Locale.ROOT);
        String categoryId = match.getRule().getCategory().getId().toString().toUpperCase(Locale.ROOT);

        WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
        dto.setId("lt" + idx);
        dto.setType(mapType(categoryId, ruleId));
        dto.setCategory("error");                       // LT = objective errors only
        dto.setSeverity(mapSeverity(categoryId));
        dto.setSpan(new WritingEvaluateResponse.SpanDto(from, to));
        dto.setOriginal(original);
        dto.setSuggestion(suggestion);
        dto.setReason(buildReason(match));
        return dto;
    }

    /** Map LT category / rule to our fine-grained error type. */
    private String mapType(String categoryId, String ruleId) {
        // Rule-level overrides first
        if (ruleId.contains("SUBJECT_VERB"))  return "subject_verb";
        if (ruleId.contains("VERB_TENSE"))    return "tense";
        if (ruleId.contains("ARTICLE"))       return "article";
        if (ruleId.contains("PREPOSITION"))   return "preposition";

        // Category-level mapping
        return switch (categoryId) {
            case "GRAMMAR"         -> "syntax";
            case "TYPOS"           -> "spelling";
            case "PUNCTUATION"     -> "punctuation";
            case "CONFUSED_WORDS"  -> "word_choice";
            case "CASING"          -> "spelling";
            case "COMPOUNDING"     -> "spelling";
            case "SEMANTICS"       -> "logic";
            default                -> "syntax";
        };
    }

    /** Map LT category to severity. */
    private String mapSeverity(String categoryId) {
        return switch (categoryId) {
            case "TYPOS", "CONFUSED_WORDS", "GRAMMAR" -> "major";
            default -> "minor";
        };
    }

    /** Build a Chinese-prefixed reason string. */
    private String buildReason(RuleMatch match) {
        String ltMessage = match.getMessage();
        // Strip HTML tags that LT sometimes includes
        if (ltMessage != null) {
            ltMessage = ltMessage.replaceAll("<[^>]+>", "");
        }
        String categoryId = match.getRule().getCategory().getId().toString().toUpperCase(Locale.ROOT);
        String prefix = switch (categoryId) {
            case "GRAMMAR"        -> "语法错误：";
            case "TYPOS"          -> "拼写错误：";
            case "PUNCTUATION"    -> "标点错误：";
            case "CONFUSED_WORDS" -> "混淆词：";
            case "CASING"         -> "大小写：";
            case "COMPOUNDING"    -> "复合词：";
            case "SEMANTICS"      -> "语义问题：";
            default               -> "语法错误：";
        };
        return prefix + (ltMessage == null ? "" : ltMessage);
    }
}
