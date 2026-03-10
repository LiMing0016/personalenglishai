package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GrammarCheckServiceImpl implements GrammarCheckService {

    private static final Logger log = LoggerFactory.getLogger(GrammarCheckServiceImpl.class);

    private final LanguageToolService languageToolService;
    private final SaplingService saplingService;
    private final TextGearsService textGearsService;
    private final TrinkaService trinkaService;

    /**
     * Sapling 段落级缓存：key = 段落文本, value = 该段落的错误列表。
     * 用户每次按键触发 debounced 检查时，只有改动的段落才需要重新调 Sapling API，
     * 未改动的段落直接复用缓存结果，大幅减少 API 调用。
     */
    private final ConcurrentHashMap<String, List<WritingEvaluateResponse.ErrorDto>> saplingCache =
            new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 200;

    /**
     * TextGears 段落级缓存，同 Sapling 缓存策略。
     */
    private final ConcurrentHashMap<String, List<WritingEvaluateResponse.ErrorDto>> textGearsCache =
            new ConcurrentHashMap<>();

    /**
     * Trinka 段落级缓存，同 Sapling 缓存策略。
     */
    private final ConcurrentHashMap<String, List<WritingEvaluateResponse.ErrorDto>> trinkaCache =
            new ConcurrentHashMap<>();

    public GrammarCheckServiceImpl(LanguageToolService languageToolService,
                                   SaplingService saplingService,
                                   TextGearsService textGearsService,
                                   TrinkaService trinkaService) {
        this.languageToolService = languageToolService;
        this.saplingService = saplingService;
        this.textGearsService = textGearsService;
        this.trinkaService = trinkaService;
    }

    @Override
    public List<WritingEvaluateResponse.ErrorDto> check(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 统一换行符：Windows \r\n → \n，防止 LT/Sapling span 偏移
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        long start = System.currentTimeMillis();

        // Run LT, Sapling, TextGears and Trinka in parallel
        final String normalizedText = text;
        CompletableFuture<List<WritingEvaluateResponse.ErrorDto>> ltFuture =
                CompletableFuture.supplyAsync(() -> languageToolService.check(normalizedText));
        CompletableFuture<List<WritingEvaluateResponse.ErrorDto>> saplingFuture =
                CompletableFuture.supplyAsync(() -> checkSaplingWithCache(normalizedText));
        CompletableFuture<List<WritingEvaluateResponse.ErrorDto>> textGearsFuture =
                CompletableFuture.supplyAsync(() -> checkTextGearsWithCache(normalizedText));
        CompletableFuture<List<WritingEvaluateResponse.ErrorDto>> trinkaFuture =
                CompletableFuture.supplyAsync(() -> checkTrinkaWithCache(normalizedText));

        List<WritingEvaluateResponse.ErrorDto> ltErrors;
        List<WritingEvaluateResponse.ErrorDto> saplingErrors;
        List<WritingEvaluateResponse.ErrorDto> textGearsErrors;
        List<WritingEvaluateResponse.ErrorDto> trinkaErrors;
        try {
            ltErrors = ltFuture.join();
            saplingErrors = saplingFuture.join();
            textGearsErrors = textGearsFuture.join();
            trinkaErrors = trinkaFuture.join();
        } catch (Exception e) {
            log.warn("Grammar check parallel execution failed: {}", e.getMessage());
            ltErrors = languageToolService.check(normalizedText);
            saplingErrors = List.of();
            textGearsErrors = List.of();
            trinkaErrors = List.of();
        }

        // Merge: LT as primary, others as secondary (dedup by original text overlap)
        List<WritingEvaluateResponse.ErrorDto> merged = mergeErrors(ltErrors, saplingErrors);
        merged = mergeErrors(merged, textGearsErrors);
        merged = mergeErrors(merged, trinkaErrors);

        // Sort by span.start and assign sequential IDs
        merged.sort(Comparator.comparingInt(e -> e.getSpan() != null ? e.getSpan().getStart() : 0));
        int id = 1;
        for (var e : merged) {
            e.setId("gc" + id++);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Grammar check done. lt={} sapling={} textgears={} trinka={} merged={} elapsed={}ms",
                ltErrors.size(), saplingErrors.size(), textGearsErrors.size(), trinkaErrors.size(),
                merged.size(), elapsed);

        return merged;
    }

    /**
     * 按段落拆分文本，缓存命中的段落直接复用，未命中的调 Sapling API。
     * 结果中 span 偏移量需要加上段落在全文中的起始位置。
     */
    /**
     * 按段落拆分，缓存未改动的段落。
     * <p>
     * Sapling 接收 trimmed 段落文本，返回的 span 基于 trimmed（0-based）。
     * 缓存 key = trimmed, value = trimmed 相对 span。
     * 读取时加上 trimmed 在全文中的偏移即可。
     */
    private List<WritingEvaluateResponse.ErrorDto> checkSaplingWithCache(String text) {
        String[] paragraphs = text.split("(?<=\\n)");
        List<WritingEvaluateResponse.ErrorDto> allErrors = new ArrayList<>();
        int offset = 0;

        for (String para : paragraphs) {
            if (para.isBlank()) {
                offset += para.length();
                continue;
            }

            String trimmed = para.trim();
            // trimmed 在全文中的起始位置
            int leadingWs = para.indexOf(trimmed.charAt(0));
            int trimmedBase = offset + Math.max(0, leadingWs);

            List<WritingEvaluateResponse.ErrorDto> cached = saplingCache.get(trimmed);
            if (cached != null) {
                // 缓存命中：span 相对于 trimmed，加 trimmedBase 转全文
                for (var e : cached) {
                    WritingEvaluateResponse.ErrorDto copy = copyError(e);
                    copy.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + trimmedBase,
                            e.getSpan().getEnd() + trimmedBase));
                    allErrors.add(copy);
                }
            } else {
                // 缓存未命中：用 trimmed 调 API，span 直接基于 trimmed（0-based）
                List<WritingEvaluateResponse.ErrorDto> fresh = saplingService.check(trimmed);
                List<WritingEvaluateResponse.ErrorDto> cacheEntry = new ArrayList<>();
                for (var e : fresh) {
                    // 缓存保存 trimmed 相对 span（原样）
                    cacheEntry.add(copyError(e));
                    // allErrors 保存全文 span
                    WritingEvaluateResponse.ErrorDto global = copyError(e);
                    global.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + trimmedBase,
                            e.getSpan().getEnd() + trimmedBase));
                    allErrors.add(global);
                }
                if (saplingCache.size() > MAX_CACHE_SIZE) {
                    saplingCache.clear();
                }
                saplingCache.put(trimmed, cacheEntry);
            }
            offset += para.length();
        }
        return allErrors;
    }

    private WritingEvaluateResponse.ErrorDto copyError(WritingEvaluateResponse.ErrorDto src) {
        WritingEvaluateResponse.ErrorDto copy = new WritingEvaluateResponse.ErrorDto();
        copy.setType(src.getType());
        copy.setCategory(src.getCategory());
        copy.setSeverity(src.getSeverity());
        copy.setOriginal(src.getOriginal());
        copy.setSuggestion(src.getSuggestion());
        copy.setReason(src.getReason());
        copy.setLangCategory(src.getLangCategory());
        copy.setAlternatives(src.getAlternatives());
        copy.setSpan(new WritingEvaluateResponse.SpanDto(
                src.getSpan().getStart(), src.getSpan().getEnd()));
        return copy;
    }

    private List<WritingEvaluateResponse.ErrorDto> mergeErrors(
            List<WritingEvaluateResponse.ErrorDto> primary,
            List<WritingEvaluateResponse.ErrorDto> secondary) {

        List<WritingEvaluateResponse.ErrorDto> merged = new ArrayList<>(primary);

        Set<String> existingOriginals = new HashSet<>();
        for (var e : primary) {
            if (e.getOriginal() != null) {
                existingOriginals.add(e.getOriginal().toLowerCase(Locale.ROOT).trim());
            }
        }

        for (var e : secondary) {
            String orig = e.getOriginal();
            if (orig == null || orig.isBlank()) continue;
            String key = orig.toLowerCase(Locale.ROOT).trim();

            // Exact match dedup
            if (existingOriginals.contains(key)) continue;

            // Substring overlap dedup
            boolean overlap = false;
            for (String existing : existingOriginals) {
                if (key.contains(existing) || existing.contains(key)) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;

            // Span overlap dedup: skip if Sapling error overlaps with an existing LT error
            if (e.getSpan() != null) {
                boolean spanOverlap = merged.stream().anyMatch(existing ->
                        existing.getSpan() != null &&
                        e.getSpan().getStart() < existing.getSpan().getEnd() &&
                        e.getSpan().getEnd() > existing.getSpan().getStart());
                if (spanOverlap) continue;
            }

            merged.add(e);
            existingOriginals.add(key);
        }

        return merged;
    }

    /**
     * TextGears 段落级缓存检查，同 Sapling 缓存策略。
     */
    private List<WritingEvaluateResponse.ErrorDto> checkTextGearsWithCache(String text) {
        String[] paragraphs = text.split("(?<=\\n)");
        List<WritingEvaluateResponse.ErrorDto> allErrors = new ArrayList<>();
        int offset = 0;

        for (String para : paragraphs) {
            if (para.isBlank()) {
                offset += para.length();
                continue;
            }

            String trimmed = para.trim();
            int leadingWs = para.indexOf(trimmed.charAt(0));
            int trimmedBase = offset + Math.max(0, leadingWs);

            List<WritingEvaluateResponse.ErrorDto> cached = textGearsCache.get(trimmed);
            if (cached != null) {
                for (var e : cached) {
                    WritingEvaluateResponse.ErrorDto copy = copyError(e);
                    copy.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + trimmedBase,
                            e.getSpan().getEnd() + trimmedBase));
                    allErrors.add(copy);
                }
            } else {
                List<WritingEvaluateResponse.ErrorDto> fresh = textGearsService.check(trimmed);
                List<WritingEvaluateResponse.ErrorDto> cacheEntry = new ArrayList<>();
                for (var e : fresh) {
                    cacheEntry.add(copyError(e));
                    WritingEvaluateResponse.ErrorDto global = copyError(e);
                    global.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + trimmedBase,
                            e.getSpan().getEnd() + trimmedBase));
                    allErrors.add(global);
                }
                if (textGearsCache.size() > MAX_CACHE_SIZE) {
                    textGearsCache.clear();
                }
                textGearsCache.put(trimmed, cacheEntry);
            }
            offset += para.length();
        }
        return allErrors;
    }

    /**
     * Trinka 段落级缓存检查，同 Sapling/TextGears 缓存策略。
     * Trinka /check/paragraph 接口对长文本返回 500，必须按段落拆分。
     */
    private List<WritingEvaluateResponse.ErrorDto> checkTrinkaWithCache(String text) {
        String[] paragraphs = text.split("(?<=\\n)");
        List<WritingEvaluateResponse.ErrorDto> allErrors = new ArrayList<>();
        int offset = 0;

        for (String para : paragraphs) {
            if (para.isBlank()) {
                offset += para.length();
                continue;
            }

            String trimmed = para.trim();
            int leadingWs = para.indexOf(trimmed.charAt(0));
            int trimmedBase = offset + Math.max(0, leadingWs);

            List<WritingEvaluateResponse.ErrorDto> cached = trinkaCache.get(trimmed);
            if (cached != null) {
                for (var e : cached) {
                    WritingEvaluateResponse.ErrorDto copy = copyError(e);
                    copy.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + trimmedBase,
                            e.getSpan().getEnd() + trimmedBase));
                    allErrors.add(copy);
                }
            } else {
                List<WritingEvaluateResponse.ErrorDto> fresh = trinkaService.check(trimmed);
                List<WritingEvaluateResponse.ErrorDto> cacheEntry = new ArrayList<>();
                for (var e : fresh) {
                    cacheEntry.add(copyError(e));
                    WritingEvaluateResponse.ErrorDto global = copyError(e);
                    global.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + trimmedBase,
                            e.getSpan().getEnd() + trimmedBase));
                    allErrors.add(global);
                }
                if (trinkaCache.size() > MAX_CACHE_SIZE) {
                    trinkaCache.clear();
                }
                trinkaCache.put(trimmed, cacheEntry);
            }
            offset += para.length();
        }
        return allErrors;
    }
}
