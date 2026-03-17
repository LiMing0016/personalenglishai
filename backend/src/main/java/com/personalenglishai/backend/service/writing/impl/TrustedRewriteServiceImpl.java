package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.RewriteApplyRequest;
import com.personalenglishai.backend.dto.writing.RewriteApplyResponse;
import com.personalenglishai.backend.dto.writing.TrustedRewriteSegmentDto;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.TrustedRewriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TrustedRewriteServiceImpl implements TrustedRewriteService {

    private static final Logger log = LoggerFactory.getLogger(TrustedRewriteServiceImpl.class);
    private static final String TRUSTED_REWRITE_PREFIX = "rewrite:trusted:";
    private static final Duration TRUSTED_REWRITE_TTL = Duration.ofDays(14);
    private static final int CONTEXT_WINDOW = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GrammarCheckService grammarCheckService;

    public TrustedRewriteServiceImpl(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     GrammarCheckService grammarCheckService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.grammarCheckService = grammarCheckService;
    }

    @Override
    public RewriteApplyResponse applyTrustedRewrite(Long userId, RewriteApplyRequest request) {
        if (userId == null || request == null || isBlank(request.getDocId())) {
            return new RewriteApplyResponse(false, 0, "缺少文档信息，无法建立可信润色记录", null);
        }
        String tier = normalizeTier(request.getTier());
        if (!isTrustedTier(tier)) {
            return new RewriteApplyResponse(false, 0, "当前润色档次不进入 trusted rewrite", null);
        }

        String essay = normalizeText(request.getEssay());
        ReplacementPosition position = resolveReplacement(essay, request.getStart(), request.getEnd(), request.getOriginal());
        if (position == null) {
            return new RewriteApplyResponse(false, 0, "原句已变化，未建立 trusted rewrite", null);
        }

        String replacement = normalizeText(request.getReplacement()).trim();
        List<WritingEvaluateResponse.ErrorDto> liteErrors = grammarCheckService.check(replacement, "lite");
        long hardErrorCount = liteErrors.stream().filter(this::isHardError).count();
        if (hardErrorCount > 0) {
            return new RewriteApplyResponse(false, (int) hardErrorCount, "润色句仍有基础错误，未进入 trusted rewrite", null);
        }

        String rewrittenEssay = essay.substring(0, position.start()) + replacement + essay.substring(position.end());
        TrustedRewriteSegmentDto record = buildRecord(request.getDocId(), rewrittenEssay, position.start(), replacement, tier);
        saveRecord(userId, request.getDocId(), record);
        return new RewriteApplyResponse(true, 0, "已登记 trusted rewrite", record);
    }

    @Override
    public List<WritingEvaluateResponse.ErrorDto> filterTrustedTrinkaSuggestions(Long userId,
                                                                                 String docId,
                                                                                 String text,
                                                                                 List<WritingEvaluateResponse.ErrorDto> errors) {
        if (userId == null || isBlank(docId) || errors == null || errors.isEmpty()) {
            return errors;
        }
        List<TrustedRewriteSegmentDto> records = loadRecords(userId, docId);
        if (records.isEmpty()) {
            return errors;
        }

        String normalizedText = normalizeText(text);
        List<MatchedRange> matchedRanges = records.stream()
                .map(record -> findRange(normalizedText, record))
                .filter(range -> range != null)
                .sorted(Comparator.comparingInt(MatchedRange::start))
                .toList();
        if (matchedRanges.isEmpty()) {
            return errors;
        }

        List<WritingEvaluateResponse.ErrorDto> filtered = new ArrayList<>();
        for (WritingEvaluateResponse.ErrorDto error : errors) {
            if (!shouldSuppress(error, matchedRanges)) {
                filtered.add(error);
            }
        }
        return filtered;
    }

    @Override
    public void clearTrustedRewrites(Long userId, String docId) {
        if (userId == null || isBlank(docId)) {
            return;
        }
        try {
            redisTemplate.delete(redisKey(userId, docId));
        } catch (Exception e) {
            log.warn("Failed to clear trusted rewrite records. userId={} docId={} reason={}", userId, docId, e.getMessage());
        }
    }

    private boolean shouldSuppress(WritingEvaluateResponse.ErrorDto error, List<MatchedRange> matchedRanges) {
        if (error == null || error.getSpan() == null) {
            return false;
        }
        if (!"trinka".equalsIgnoreCase(error.getEngine())) {
            return false;
        }
        if (!"suggestion".equalsIgnoreCase(error.getCategory())) {
            return false;
        }
        int start = error.getSpan().getStart();
        int end = error.getSpan().getEnd();
        for (MatchedRange range : matchedRanges) {
            if (start >= range.start() && end <= range.end()) {
                return true;
            }
        }
        return false;
    }

    private boolean isHardError(WritingEvaluateResponse.ErrorDto error) {
        return error != null && !"suggestion".equalsIgnoreCase(error.getCategory());
    }

    private TrustedRewriteSegmentDto buildRecord(String docId, String essay, int sentenceStart, String sentenceText, String tier) {
        TrustedRewriteSegmentDto dto = new TrustedRewriteSegmentDto();
        dto.setDocId(docId);
        dto.setSentenceText(sentenceText);
        dto.setNormalizedTextHash(hashNormalized(sentenceText));
        dto.setLeftContext(extractLeftContext(essay, sentenceStart));
        dto.setRightContext(extractRightContext(essay, sentenceStart + sentenceText.length()));
        dto.setTier(tier);
        dto.setSource("rewrite");
        dto.setUpdatedAt(System.currentTimeMillis());
        return dto;
    }

    private void saveRecord(Long userId, String docId, TrustedRewriteSegmentDto record) {
        String key = redisKey(userId, docId);
        List<TrustedRewriteSegmentDto> records = loadRecords(userId, docId);
        String fingerprint = fingerprint(record);
        records.removeIf(existing -> fingerprint(existing).equals(fingerprint));
        records.add(record);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(records), TRUSTED_REWRITE_TTL);
        } catch (Exception e) {
            log.warn("Failed to save trusted rewrite. userId={} docId={} reason={}", userId, docId, e.getMessage());
        }
    }

    private List<TrustedRewriteSegmentDto> loadRecords(Long userId, String docId) {
        String key = redisKey(userId, docId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<TrustedRewriteSegmentDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to load trusted rewrite records. userId={} docId={} reason={}", userId, docId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private MatchedRange findRange(String text, TrustedRewriteSegmentDto record) {
        if (record == null || isBlank(record.getSentenceText()) || isBlank(text)) {
            return null;
        }
        String needle = record.getSentenceText();
        List<Integer> matches = new ArrayList<>();
        int from = 0;
        while (from <= text.length() - needle.length()) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) {
                break;
            }
            matches.add(idx);
            from = idx + 1;
        }
        if (matches.isEmpty()) {
            return null;
        }
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        String normalizedLeft = normalizeSnippet(record.getLeftContext());
        String normalizedRight = normalizeSnippet(record.getRightContext());
        for (Integer match : matches) {
            int score = 0;
            String left = normalizeSnippet(extractLeftContext(text, match));
            String right = normalizeSnippet(extractRightContext(text, match + needle.length()));
            if (!normalizedLeft.isBlank() && left.endsWith(normalizedLeft)) {
                score += 2;
            }
            if (!normalizedRight.isBlank() && right.startsWith(normalizedRight)) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                bestIndex = match;
            }
        }
        if (bestIndex < 0) {
            bestIndex = matches.get(0);
        }
        return new MatchedRange(bestIndex, bestIndex + needle.length());
    }

    private ReplacementPosition resolveReplacement(String essay, Integer requestedStart, Integer requestedEnd, String original) {
        if (essay == null || original == null) {
            return null;
        }
        int start = Math.max(0, Math.min(requestedStart == null ? 0 : requestedStart, essay.length()));
        int end = Math.max(0, Math.min(requestedEnd == null ? start : requestedEnd, essay.length()));
        if (end < start) {
            int temp = start;
            start = end;
            end = temp;
        }
        if (start < end && essay.substring(start, end).equals(original)) {
            return new ReplacementPosition(start, end);
        }
        int fallback = findClosestMatch(essay, original, start);
        if (fallback < 0) {
            return null;
        }
        return new ReplacementPosition(fallback, fallback + original.length());
    }

    private int findClosestMatch(String text, String needle, int hint) {
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        int searchFrom = 0;
        while (searchFrom <= text.length() - needle.length()) {
            int idx = text.indexOf(needle, searchFrom);
            if (idx < 0) {
                break;
            }
            int distance = Math.abs(idx - hint);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = idx;
            }
            searchFrom = idx + 1;
        }
        return bestIndex;
    }

    private String redisKey(Long userId, String docId) {
        return TRUSTED_REWRITE_PREFIX + userId + ":" + docId;
    }

    private String extractLeftContext(String text, int start) {
        int from = Math.max(0, start - CONTEXT_WINDOW);
        return text.substring(from, Math.max(from, start));
    }

    private String extractRightContext(String text, int end) {
        int to = Math.min(text.length(), end + CONTEXT_WINDOW);
        return text.substring(Math.min(end, to), to);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private String normalizeSnippet(String snippet) {
        return normalizeText(snippet).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeTier(String tier) {
        return tier == null ? "" : tier.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isTrustedTier(String tier) {
        return "advanced".equals(tier) || "perfect".equals(tier);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String fingerprint(TrustedRewriteSegmentDto record) {
        return record.getNormalizedTextHash() + "|" + normalizeSnippet(record.getLeftContext()) + "|" + normalizeSnippet(record.getRightContext()) + "|" + normalizeTier(record.getTier());
    }

    private String hashNormalized(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalizeSnippet(text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash trusted rewrite text", e);
        }
    }

    private record MatchedRange(int start, int end) {}
    private record ReplacementPosition(int start, int end) {}
}
