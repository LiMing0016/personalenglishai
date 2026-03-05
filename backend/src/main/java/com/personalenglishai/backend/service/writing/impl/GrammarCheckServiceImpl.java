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

    /**
     * Sapling 段落级缓存：key = 段落文本, value = 该段落的错误列表。
     * 用户每次按键触发 debounced 检查时，只有改动的段落才需要重新调 Sapling API，
     * 未改动的段落直接复用缓存结果，大幅减少 API 调用。
     */
    private final ConcurrentHashMap<String, List<WritingEvaluateResponse.ErrorDto>> saplingCache =
            new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 200;

    public GrammarCheckServiceImpl(LanguageToolService languageToolService,
                                   SaplingService saplingService) {
        this.languageToolService = languageToolService;
        this.saplingService = saplingService;
    }

    @Override
    public List<WritingEvaluateResponse.ErrorDto> check(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        long start = System.currentTimeMillis();

        // Run LT (full text) and Sapling (paragraph-cached) in parallel
        CompletableFuture<List<WritingEvaluateResponse.ErrorDto>> ltFuture =
                CompletableFuture.supplyAsync(() -> languageToolService.check(text));
        CompletableFuture<List<WritingEvaluateResponse.ErrorDto>> saplingFuture =
                CompletableFuture.supplyAsync(() -> checkSaplingWithCache(text));

        List<WritingEvaluateResponse.ErrorDto> ltErrors;
        List<WritingEvaluateResponse.ErrorDto> saplingErrors;
        try {
            ltErrors = ltFuture.join();
            saplingErrors = saplingFuture.join();
        } catch (Exception e) {
            log.warn("Grammar check parallel execution failed: {}", e.getMessage());
            ltErrors = languageToolService.check(text);
            saplingErrors = List.of();
        }

        // Merge: LT as primary, Sapling as secondary (dedup by original text overlap)
        List<WritingEvaluateResponse.ErrorDto> merged = mergeErrors(ltErrors, saplingErrors);

        // Sort by span.start and assign sequential IDs
        merged.sort(Comparator.comparingInt(e -> e.getSpan() != null ? e.getSpan().getStart() : 0));
        int id = 1;
        for (var e : merged) {
            e.setId("gc" + id++);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Grammar check done. lt={} sapling={} merged={} elapsed={}ms",
                ltErrors.size(), saplingErrors.size(), merged.size(), elapsed);

        return merged;
    }

    /**
     * 按段落拆分文本，缓存命中的段落直接复用，未命中的调 Sapling API。
     * 结果中 span 偏移量需要加上段落在全文中的起始位置。
     */
    private List<WritingEvaluateResponse.ErrorDto> checkSaplingWithCache(String text) {
        // 按双换行或单换行拆段落
        String[] paragraphs = text.split("(?<=\\n)");
        List<WritingEvaluateResponse.ErrorDto> allErrors = new ArrayList<>();
        int offset = 0;

        for (String para : paragraphs) {
            if (para.isBlank()) {
                offset += para.length();
                continue;
            }
            String trimmed = para.trim();
            List<WritingEvaluateResponse.ErrorDto> cached = saplingCache.get(trimmed);
            if (cached != null) {
                // 复用缓存，调整 span 偏移
                for (var e : cached) {
                    WritingEvaluateResponse.ErrorDto copy = new WritingEvaluateResponse.ErrorDto();
                    copy.setType(e.getType());
                    copy.setCategory(e.getCategory());
                    copy.setSeverity(e.getSeverity());
                    copy.setOriginal(e.getOriginal());
                    copy.setSuggestion(e.getSuggestion());
                    copy.setReason(e.getReason());
                    int paraOffset = text.indexOf(para, offset);
                    int base = paraOffset >= 0 ? paraOffset : offset;
                    copy.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() + base,
                            e.getSpan().getEnd() + base));
                    allErrors.add(copy);
                }
            } else {
                // 未命中缓存，调 API
                List<WritingEvaluateResponse.ErrorDto> fresh = saplingService.check(para);
                // 存入缓存（span 相对于段落自身，从 0 开始）
                List<WritingEvaluateResponse.ErrorDto> cacheEntry = new ArrayList<>();
                int paraOffset = text.indexOf(para, offset);
                int base = paraOffset >= 0 ? paraOffset : offset;
                for (var e : fresh) {
                    // 缓存中保存相对于段落的 span
                    WritingEvaluateResponse.ErrorDto cacheItem = new WritingEvaluateResponse.ErrorDto();
                    cacheItem.setType(e.getType());
                    cacheItem.setCategory(e.getCategory());
                    cacheItem.setSeverity(e.getSeverity());
                    cacheItem.setOriginal(e.getOriginal());
                    cacheItem.setSuggestion(e.getSuggestion());
                    cacheItem.setReason(e.getReason());
                    cacheItem.setSpan(new WritingEvaluateResponse.SpanDto(
                            e.getSpan().getStart() - base,
                            e.getSpan().getEnd() - base));
                    cacheEntry.add(cacheItem);
                    allErrors.add(e);
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
}
