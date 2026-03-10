package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.GrammarSuppressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GrammarSuppressServiceImpl implements GrammarSuppressService {

    private static final Logger log = LoggerFactory.getLogger(GrammarSuppressServiceImpl.class);

    /** Dismiss: user explicitly said "don't show again". */
    private static final String DISMISS_PREFIX = "grammar:dismiss:";
    private static final Duration DISMISS_TTL = Duration.ofHours(72);

    /** Fix: user applied a replacement, suppress reverse flip-flop. */
    private static final String FIX_PREFIX = "grammar:fix:";
    private static final Duration FIX_TTL = Duration.ofMinutes(30);

    /** Context window: chars before/after error span for hash. */
    private static final int CONTEXT_WINDOW = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GrammarSuppressServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════

    @Override
    public void suppress(Long userId, String docId, String original, String suggestion,
                         String ruleType, String engine, String context, String action) {
        boolean isDismiss = !"fix".equals(action);
        String key = isDismiss ? dismissKey(userId, docId) : fixKey(userId, docId);
        Duration ttl = isDismiss ? DISMISS_TTL : FIX_TTL;

        SuppressionEntry entry = new SuppressionEntry();
        entry.original = normalize(original);
        entry.suggestion = normalize(suggestion);
        entry.ruleType = ruleType != null ? ruleType : "";
        entry.engine = engine != null ? engine : "";
        entry.contextHash = hashContext(context);
        entry.action = action != null ? action : "dismiss";

        try {
            List<SuppressionEntry> entries = loadEntries(key);

            // Dedup by fingerprint
            String fp = entry.fingerprint();
            if (entries.stream().anyMatch(e -> e.fingerprint().equals(fp))) {
                refreshTtl(key, ttl);
                return;
            }
            entries.add(entry);

            // For "fix": also add the reverse entry to prevent flip-flop
            if ("fix".equals(action)) {
                SuppressionEntry reverse = new SuppressionEntry();
                reverse.original = entry.suggestion;
                reverse.suggestion = entry.original;
                reverse.ruleType = entry.ruleType;
                reverse.engine = entry.engine;
                reverse.contextHash = entry.contextHash;
                reverse.action = "fix_reverse";
                String reverseFp = reverse.fingerprint();
                if (entries.stream().noneMatch(e -> e.fingerprint().equals(reverseFp))) {
                    entries.add(reverse);
                }
            }

            saveEntries(key, entries, ttl);
            log.debug("Suppress added [{}]: engine={} original='{}' suggestion='{}'",
                    action, engine, original, suggestion);
        } catch (Exception e) {
            log.warn("Failed to save suppression: {}", e.getMessage());
        }
    }

    @Override
    public List<WritingEvaluateResponse.ErrorDto> filterSuppressed(
            Long userId, String docId,
            List<WritingEvaluateResponse.ErrorDto> errors, String text) {

        if (errors == null || errors.isEmpty()) return errors;
        if (userId == null || docId == null || docId.isBlank()) return errors;

        List<SuppressionEntry> dismissEntries;
        List<SuppressionEntry> fixEntries;
        try {
            dismissEntries = loadEntries(dismissKey(userId, docId));
            fixEntries = loadEntries(fixKey(userId, docId));
        } catch (Exception e) {
            log.warn("Failed to load suppressions: {}", e.getMessage());
            return errors;
        }

        if (dismissEntries.isEmpty() && fixEntries.isEmpty()) return errors;

        Set<String> dismissFingerprints = dismissEntries.stream()
                .map(SuppressionEntry::fingerprint).collect(Collectors.toSet());
        Set<String> fixFingerprints = fixEntries.stream()
                .map(SuppressionEntry::fingerprint).collect(Collectors.toSet());

        List<WritingEvaluateResponse.ErrorDto> filtered = new ArrayList<>();
        for (var error : errors) {
            if (isDismissed(error, text, dismissFingerprints)) {
                log.debug("Dismissed: engine={} original='{}' suggestion='{}'",
                        error.getEngine(), error.getOriginal(), error.getSuggestion());
                continue;
            }
            if (isRecentlyFixed(error, text, fixFingerprints, fixEntries)) {
                log.debug("Fix-suppressed: engine={} original='{}' suggestion='{}'",
                        error.getEngine(), error.getOriginal(), error.getSuggestion());
                continue;
            }
            filtered.add(error);
        }
        return filtered;
    }

    @Override
    public void clearAll(Long userId, String docId) {
        try {
            redisTemplate.delete(dismissKey(userId, docId));
            redisTemplate.delete(fixKey(userId, docId));
        } catch (Exception e) {
            log.warn("Failed to clear suppressions: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Matching logic
    // ════════════════════════════════════════════════════════════════

    /**
     * Dismiss: exact fingerprint only (engine + ruleType + original + suggestion + contextHash).
     * No fallback — avoid accidentally suppressing real errors in other contexts.
     */
    private boolean isDismissed(WritingEvaluateResponse.ErrorDto error, String text,
                                 Set<String> dismissFingerprints) {
        String orig = error.getOriginal();
        String sug = error.getSuggestion();
        if (orig == null || sug == null) return false;

        String contextHash = computeContextHash(text, error.getSpan());
        String engine = error.getEngine() != null ? error.getEngine() : "";
        String ruleType = error.getType() != null ? error.getType() : "";

        String fp = fingerprint(normalize(orig), normalize(sug), ruleType, engine, contextHash);
        return dismissFingerprints.contains(fp);
    }

    /**
     * Fix: exact fingerprint first, then weak fallback (original + suggestion only)
     * to catch flip-flop across slight context changes. Only applies within the short TTL.
     */
    private boolean isRecentlyFixed(WritingEvaluateResponse.ErrorDto error, String text,
                                     Set<String> fixFingerprints,
                                     List<SuppressionEntry> fixEntries) {
        String orig = error.getOriginal();
        String sug = error.getSuggestion();
        if (orig == null || sug == null) return false;

        String origNorm = normalize(orig);
        String sugNorm = normalize(sug);
        String contextHash = computeContextHash(text, error.getSpan());
        String engine = error.getEngine() != null ? error.getEngine() : "";
        String ruleType = error.getType() != null ? error.getType() : "";

        // Exact fingerprint match
        String fp = fingerprint(origNorm, sugNorm, ruleType, engine, contextHash);
        if (fixFingerprints.contains(fp)) return true;

        // Weak fallback: original + suggestion only (anti flip-flop, short TTL protects against false positives)
        for (var entry : fixEntries) {
            if (entry.original.equals(origNorm) && entry.suggestion.equals(sugNorm)) {
                return true;
            }
        }

        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Compute context hash: take a window of ±CONTEXT_WINDOW chars around the error span,
     * normalize, then SHA-256. This is stable against edits outside the window.
     */
    private String computeContextHash(String text, WritingEvaluateResponse.SpanDto span) {
        if (text == null || span == null) return "";
        int start = span.getStart();
        int end = span.getEnd();
        if (start < 0 || end > text.length() || start >= end) return "";

        int windowStart = Math.max(0, start - CONTEXT_WINDOW);
        int windowEnd = Math.min(text.length(), end + CONTEXT_WINDOW);
        String window = text.substring(windowStart, windowEnd);
        return hashContext(window);
    }

    private String dismissKey(Long userId, String docId) {
        return DISMISS_PREFIX + userId + ":" + docId;
    }

    private String fixKey(Long userId, String docId) {
        return FIX_PREFIX + userId + ":" + docId;
    }

    private List<SuppressionEntry> loadEntries(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) return new ArrayList<>();
            return objectMapper.readValue(json, new TypeReference<List<SuppressionEntry>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse suppression entries: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveEntries(String key, List<SuppressionEntry> entries, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(entries);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Failed to save suppression entries: {}", e.getMessage());
        }
    }

    private void refreshTtl(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
        } catch (Exception ignored) {}
    }

    static String normalize(String s) {
        return s != null ? s.toLowerCase().trim() : "";
    }

    static String hashContext(String context) {
        if (context == null || context.isBlank()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(normalize(context).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(normalize(context).hashCode());
        }
    }

    private static String fingerprint(String original, String suggestion,
                                       String ruleType, String engine, String contextHash) {
        return engine + "|" + ruleType + "|" + original + "|" + suggestion + "|" + contextHash;
    }

    /**
     * Internal POJO for Redis JSON serialization.
     */
    static class SuppressionEntry {
        public String original;
        public String suggestion;
        public String ruleType;
        public String engine;
        public String contextHash;
        public String action;

        public String fingerprint() {
            return GrammarSuppressServiceImpl.fingerprint(original, suggestion, ruleType, engine, contextHash);
        }
    }
}
