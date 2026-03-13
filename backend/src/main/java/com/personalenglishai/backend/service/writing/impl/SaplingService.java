package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Grammar / spelling checker backed by Sapling AI HTTP API.
 * <p>
 * Complements LanguageTool — Sapling uses neural models so it catches
 * errors that rule-based engines miss (e.g. complex subject-verb agreement,
 * confused words in context).
 */
@Service
public class SaplingService {

    private static final Logger log = LoggerFactory.getLogger(SaplingService.class);

    @Value("${sapling.enabled:false}")
    private boolean enabled;

    @Value("${sapling.api-key:}")
    private String apiKey;

    @Value("${sapling.base-url:https://api.sapling.ai}")
    private String baseUrl;

    @Value("${sapling.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${sapling.proxy-enabled:${openai.client.proxy-enabled:${OPENAI_PROXY_ENABLED:false}}}")
    private boolean proxyEnabled;

    @Value("${sapling.proxy-url:${openai.client.proxy-url:${OPENAI_PROXY_URL:}}}")
    private String proxyUrl;

    @Value("${sapling.proxy-host:${openai.client.proxy-host:${OPENAI_PROXY_HOST:}}}")
    private String proxyHost;

    @Value("${sapling.proxy-port:${openai.client.proxy-port:${OPENAI_PROXY_PORT:0}}}")
    private int proxyPort;

    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public SaplingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        applyProxy(builder);
        this.httpClient = builder.build();
    }

    private void applyProxy(HttpClient.Builder builder) {
        if (builder == null || !proxyEnabled) {
            return;
        }
        try {
            if (proxyUrl != null && !proxyUrl.isBlank()) {
                URI uri = URI.create(proxyUrl.trim());
                if (uri.getHost() != null) {
                    int port = uri.getPort() > 0 ? uri.getPort() : 80;
                    builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), port)));
                    return;
                }
            }
            if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost.trim(), proxyPort)));
            }
        } catch (Exception e) {
            log.warn("Sapling proxy config ignored: {}", e.getMessage());
        }
    }

    /**
     * Check the essay via Sapling edits API.
     * Returns an empty list if disabled, no API key, or on any error.
     */
    public List<WritingEvaluateResponse.ErrorDto> check(String essay) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        if (essay == null || essay.isBlank()) {
            return List.of();
        }
        try {
            long start = System.currentTimeMillis();
            String responseBody = callSaplingApi(essay);
            List<WritingEvaluateResponse.ErrorDto> errors = parseResponse(responseBody, essay);
            long elapsed = System.currentTimeMillis() - start;
            log.info("Sapling check done. errors={} elapsed={}ms", errors.size(), elapsed);
            return errors;
        } catch (Exception e) {
            log.warn("Sapling check failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HTTP call
    // ════════════════════════════════════════════════════════════════

    private String callSaplingApi(String essay) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "key", apiKey,
                "text", essay,
                "session_id", "eval-" + System.currentTimeMillis()
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/edits"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Sapling API returned status {}. body={}", response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        }
        return response.body();
    }

    // ════════════════════════════════════════════════════════════════
    //  Response parsing
    // ════════════════════════════════════════════════════════════════

    private List<WritingEvaluateResponse.ErrorDto> parseResponse(String body, String essay) {
        if (body == null || body.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode edits = root.path("edits");
            if (!edits.isArray()) return List.of();

            List<WritingEvaluateResponse.ErrorDto> errors = new ArrayList<>();
            int idx = 1;
            for (JsonNode edit : edits) {
                WritingEvaluateResponse.ErrorDto dto = toErrorDto(edit, essay, idx++);
                if (dto != null) {
                    errors.add(dto);
                }
            }
            return errors;
        } catch (Exception e) {
            log.warn("Sapling response parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 超过此长度的 span 视为句子级润色，跳过 */
    private static final int MAX_SPAN_LENGTH = 80;

    private WritingEvaluateResponse.ErrorDto toErrorDto(JsonNode edit, String essay, int idx) {
        // Sapling gives start/end relative to sentence, plus sentence_start as global offset
        int sentenceStart = edit.path("sentence_start").asInt(0);
        int start = sentenceStart + edit.path("start").asInt(0);
        int end = sentenceStart + edit.path("end").asInt(0);

        if (start < 0 || end <= start || end > essay.length()) return null;

        // 过滤句子级润色（整句替换），只保留词/短语级的具体错误
        if ((end - start) > MAX_SPAN_LENGTH) {
            log.debug("Sapling: skipping sentence-level edit span={}..{} len={}", start, end, end - start);
            return null;
        }

        String original = essay.substring(start, end);
        if (original.isBlank()) return null;

        String replacement = edit.path("replacement").asText("");
        String errorType = edit.path("error_type").asText("");
        String generalErrorType = edit.path("general_error_type").asText("");

        // 只保留语法/拼写/标点错误，过滤 style（润色建议）
        if ("style".equalsIgnoreCase(generalErrorType)) {
            return null;
        }

        WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
        dto.setId("sp" + idx);
        dto.setEngine("sapling");
        dto.setType(mapType(errorType, generalErrorType));
        dto.setCategory("error");
        dto.setSeverity(mapSeverity(generalErrorType));
        dto.setSpan(new WritingEvaluateResponse.SpanDto(start, end));
        dto.setOriginal(original);
        dto.setSuggestion(replacement);
        dto.setReason(buildReason(errorType, generalErrorType));
        return dto;
    }

    // ════════════════════════════════════════════════════════════════
    //  Mapping helpers
    // ════════════════════════════════════════════════════════════════

    /**
     * Map Sapling error_type (e.g. "R:VERB:SVA") and general_error_type to our type system.
     */
    private String mapType(String errorType, String generalErrorType) {
        String upper = errorType.toUpperCase(Locale.ROOT);

        // Specific error_type patterns
        if (upper.contains("SVA") || upper.contains("VERB:SUBJ"))  return "subject_verb";
        if (upper.contains("VERB:TENSE"))                          return "tense";
        if (upper.contains("VERB:FORM"))                           return "morphology";
        if (upper.contains("DET") || upper.contains("ART"))        return "article";
        if (upper.contains("PREP"))                                return "preposition";
        if (upper.contains("SPELL"))                               return "spelling";
        if (upper.contains("PUNCT"))                               return "punctuation";
        if (upper.contains("NOUN:NUM"))                            return "morphology";
        if (upper.contains("WO"))                                  return "syntax";
        if (upper.contains("MORPH"))                               return "morphology";

        // Fall back to general_error_type
        return switch (generalErrorType.toLowerCase(Locale.ROOT)) {
            case "grammar"     -> "syntax";
            case "spelling"    -> "spelling";
            case "punctuation" -> "punctuation";
            case "style"       -> "word_choice";
            default            -> "syntax";
        };
    }

    private String mapSeverity(String generalErrorType) {
        return switch (generalErrorType.toLowerCase(Locale.ROOT)) {
            case "spelling", "grammar" -> "major";
            default -> "minor";
        };
    }

    private String buildReason(String errorType, String generalErrorType) {
        String prefix = switch (generalErrorType.toLowerCase(Locale.ROOT)) {
            case "grammar"     -> "语法错误：";
            case "spelling"    -> "拼写错误：";
            case "punctuation" -> "标点错误：";
            case "style"       -> "表达建议：";
            default            -> "语法错误：";
        };
        return prefix + errorType;
    }
}


