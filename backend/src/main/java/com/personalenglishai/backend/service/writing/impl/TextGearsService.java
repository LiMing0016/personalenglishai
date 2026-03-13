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
 * Grammar / spelling checker backed by TextGears HTTP API.
 * <p>
 * Complements LanguageTool — TextGears uses AI models so it catches
 * errors that rule-based engines miss.
 * Can be used alongside or as a replacement for Sapling.
 */
@Service
public class TextGearsService {

    private static final Logger log = LoggerFactory.getLogger(TextGearsService.class);

    @Value("${textgears.enabled:false}")
    private boolean enabled;

    @Value("${textgears.api-key:}")
    private String apiKey;

    @Value("${textgears.base-url:https://api.textgears.com}")
    private String baseUrl;

    @Value("${textgears.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${textgears.language:en-US}")
    private String language;

    @Value("${textgears.ai:true}")
    private String ai;

    @Value("${textgears.proxy-enabled:${openai.client.proxy-enabled:${OPENAI_PROXY_ENABLED:false}}}")
    private boolean proxyEnabled;

    @Value("${textgears.proxy-url:${openai.client.proxy-url:${OPENAI_PROXY_URL:}}}")
    private String proxyUrl;

    @Value("${textgears.proxy-host:${openai.client.proxy-host:${OPENAI_PROXY_HOST:}}}")
    private String proxyHost;

    @Value("${textgears.proxy-port:${openai.client.proxy-port:${OPENAI_PROXY_PORT:0}}}")
    private int proxyPort;

    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public TextGearsService(ObjectMapper objectMapper) {
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
            log.warn("TextGears proxy config ignored: {}", e.getMessage());
        }
    }

    /**
     * Check the text via TextGears grammar API.
     * Returns an empty list if disabled, no API key, or on any error.
     */
    public List<WritingEvaluateResponse.ErrorDto> check(String text) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            long start = System.currentTimeMillis();

            // /grammar + /spelling 并行调用
            var grammarFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> callApiSafe(text, "/grammar"));
            var spellingFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> callApiSafe(text, "/spelling"));

            List<WritingEvaluateResponse.ErrorDto> grammarErrors = parseResponse(grammarFuture.join(), text, "tg");
            List<WritingEvaluateResponse.ErrorDto> spellingErrors = parseResponse(spellingFuture.join(), text, "ts");

            // 合并去重：grammar 为主，spelling 补充
            List<WritingEvaluateResponse.ErrorDto> merged = mergeBySpan(grammarErrors, spellingErrors);

            long elapsed = System.currentTimeMillis() - start;
            log.info("TextGears check done. grammar={} spelling={} merged={} elapsed={}ms",
                    grammarErrors.size(), spellingErrors.size(), merged.size(), elapsed);
            return merged;
        } catch (Exception e) {
            log.warn("TextGears check failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HTTP call
    // ════════════════════════════════════════════════════════════════

    private String callApiSafe(String text, String endpoint) {
        try {
            return callApi(text, endpoint);
        } catch (Exception e) {
            log.warn("TextGears {} call failed: {}", endpoint, e.getMessage());
            return null;
        }
    }

    private String callApi(String text, String endpoint) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "key", apiKey,
                "text", text,
                "language", language,
                "ai", ai
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("TextGears API {} returned status {}. body={}", endpoint, response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        }
        return response.body();
    }

    // ════════════════════════════════════════════════════════════════
    //  Response parsing
    // ════════════════════════════════════════════════════════════════

    private List<WritingEvaluateResponse.ErrorDto> parseResponse(String body, String text, String idPrefix) {
        if (body == null || body.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(body);

            // Check API-level error
            if (!root.path("status").asBoolean(false)) {
                int errorCode = root.path("error_code").asInt(0);
                String desc = root.path("description").asText("");
                log.warn("TextGears API error: code={} desc={}", errorCode, desc);
                return List.of();
            }

            JsonNode errors = root.path("response").path("errors");
            if (!errors.isArray()) return List.of();

            List<WritingEvaluateResponse.ErrorDto> result = new ArrayList<>();
            int idx = 1;
            for (JsonNode error : errors) {
                WritingEvaluateResponse.ErrorDto dto = toErrorDto(error, text, idPrefix + idx++);
                if (dto != null) {
                    result.add(dto);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("TextGears response parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Merge grammar (primary) and spelling (secondary), dedup by span overlap */
    private List<WritingEvaluateResponse.ErrorDto> mergeBySpan(
            List<WritingEvaluateResponse.ErrorDto> primary,
            List<WritingEvaluateResponse.ErrorDto> secondary) {
        List<WritingEvaluateResponse.ErrorDto> merged = new ArrayList<>(primary);
        for (var e : secondary) {
            if (e.getSpan() == null) continue;
            boolean overlap = merged.stream().anyMatch(existing ->
                    existing.getSpan() != null &&
                    e.getSpan().getStart() < existing.getSpan().getEnd() &&
                    e.getSpan().getEnd() > existing.getSpan().getStart());
            if (!overlap) {
                merged.add(e);
            }
        }
        return merged;
    }

    /** Skip sentence-level rewrites */
    private static final int MAX_SPAN_LENGTH = 80;

    private WritingEvaluateResponse.ErrorDto toErrorDto(JsonNode error, String text, String id) {
        int offset = error.path("offset").asInt(0);
        int length = error.path("length").asInt(0);
        int start = offset;
        int end = offset + length;

        if (start < 0 || end <= start || end > text.length()) return null;
        if (length > MAX_SPAN_LENGTH) return null;

        String bad = error.path("bad").asText("");
        if (bad.isBlank()) return null;

        String type = error.path("type").asText("grammar");

        // Skip style suggestions, only keep grammar/spelling/punctuation
        if ("style".equalsIgnoreCase(type) || "stylistics".equalsIgnoreCase(type)) {
            return null;
        }

        // Pick the first suggestion from "better" array
        String suggestion = "";
        JsonNode better = error.path("better");
        if (better.isArray() && !better.isEmpty()) {
            suggestion = better.get(0).asText("");
        }

        String description = error.path("description").asText("");

        WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
        dto.setId(id);
        dto.setEngine("textgears");
        dto.setType(mapType(type));
        dto.setCategory("error");
        dto.setSeverity(mapSeverity(type));
        dto.setSpan(new WritingEvaluateResponse.SpanDto(start, end));
        dto.setOriginal(bad);
        dto.setSuggestion(suggestion);
        dto.setReason(buildReason(type, description));

        // langCategory from type
        dto.setLangCategory(mapLangCategory(type));

        // Collect all alternatives from "better" array
        if (better.isArray() && better.size() > 1) {
            List<String> alternatives = new ArrayList<>();
            for (JsonNode b : better) {
                String alt = b.asText("");
                if (!alt.isBlank() && !alt.equals(bad) && !alternatives.contains(alt)) {
                    alternatives.add(alt);
                }
            }
            if (alternatives.size() > 1) {
                dto.setAlternatives(alternatives);
            }
        }

        return dto;
    }

    // ════════════════════════════════════════════════════════════════
    //  Mapping helpers
    // ════════════════════════════════════════════════════════════════

    private String mapType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "grammar" -> "syntax";
            case "spelling" -> "spelling";
            case "punctuation" -> "punctuation";
            case "style", "stylistics" -> "word_choice";
            default -> "syntax";
        };
    }

    private String mapSeverity(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "spelling", "grammar" -> "major";
            default -> "minor";
        };
    }

    private String buildReason(String type, String description) {
        if (description != null && !description.isBlank()) {
            return description;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "grammar" -> "语法错误";
            case "spelling" -> "拼写错误";
            case "punctuation" -> "标点错误";
            default -> "语法错误";
        };
    }

    private String mapLangCategory(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "grammar" -> "语法";
            case "spelling" -> "拼写";
            case "punctuation" -> "标点";
            default -> "语法";
        };
    }
}


