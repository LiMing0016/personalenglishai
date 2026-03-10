package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;

import java.util.List;

/**
 * Manages grammar suggestion suppressions (dismiss / applied fix).
 * Stored in Redis with TTL, scoped by user + document.
 *
 * Two categories with different TTL:
 * - dismiss: user explicitly said "don't show again" → 72h
 * - fix (recent): user applied a replacement, suppress reverse suggestion → 30min
 */
public interface GrammarSuppressService {

    /**
     * Suppress a grammar suggestion.
     *
     * @param userId      current user
     * @param docId       document scope
     * @param original    the flagged text
     * @param suggestion  the suggested replacement
     * @param ruleType    error type (syntax, spelling, etc.)
     * @param engine      source engine (lt, sapling, trinka, textgears, gpt)
     * @param context     context window around the error (for context hash)
     * @param action      "dismiss" or "fix"
     */
    void suppress(Long userId, String docId, String original, String suggestion,
                  String ruleType, String engine, String context, String action);

    /**
     * Filter out suppressed errors from a grammar check / evaluate result.
     */
    List<WritingEvaluateResponse.ErrorDto> filterSuppressed(
            Long userId, String docId,
            List<WritingEvaluateResponse.ErrorDto> errors, String text);

    /**
     * Clear all suppressions for a document.
     */
    void clearAll(Long userId, String docId);
}
