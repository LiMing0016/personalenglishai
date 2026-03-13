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
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Grammar / spelling checker backed by Trinka AI HTTP API.
 * <p>
 * Trinka specialises in academic & technical writing and provides
 * sentence-level analysis with detailed error categories.
 */
@Service
public class TrinkaService {

    private static final Logger log = LoggerFactory.getLogger(TrinkaService.class);

    @Value("${trinka.enabled:false}")
    private boolean enabled;

    @Value("${trinka.api-key:}")
    private String apiKey;

    @Value("${trinka.base-url:https://api-platform.trinka.ai}")
    private String baseUrl;

    @Value("${trinka.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${trinka.language:US}")
    private String language;

    @Value("${trinka.pipeline:advanced}")
    private String pipeline;

    @Value("${trinka.proxy-enabled:${openai.client.proxy-enabled:${OPENAI_PROXY_ENABLED:false}}}")
    private boolean proxyEnabled;

    @Value("${trinka.proxy-url:${openai.client.proxy-url:${OPENAI_PROXY_URL:}}}")
    private String proxyUrl;

    @Value("${trinka.proxy-host:${openai.client.proxy-host:${OPENAI_PROXY_HOST:}}}")
    private String proxyHost;

    @Value("${trinka.proxy-port:${openai.client.proxy-port:${OPENAI_PROXY_PORT:0}}}")
    private int proxyPort;

    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    public TrinkaService(ObjectMapper objectMapper) {
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
            log.warn("Trinka proxy config ignored: {}", e.getMessage());
        }
    }

    public List<WritingEvaluateResponse.ErrorDto> check(String text) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            long start = System.currentTimeMillis();
            String responseBody = callApi(text);
            List<WritingEvaluateResponse.ErrorDto> errors = parseResponse(responseBody, text);
            long elapsed = System.currentTimeMillis() - start;
            log.info("Trinka check done. errors={} elapsed={}ms", errors.size(), elapsed);
            return errors;
        } catch (HttpTimeoutException e) {
            log.info("Trinka check timed out after {}ms, skipped", timeoutMs);
            return List.of();
        } catch (Exception e) {
            log.warn("Trinka check failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HTTP call
    // ════════════════════════════════════════════════════════════════

    private String callApi(String text) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "paragraph", text,
                "language", language,
                "pipeline", pipeline,
                "is_sensitive_data", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v2/plugin/check/paragraph"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Trinka API returned status {}. body={}", response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
            return null;
        }
        return response.body();
    }

    // ════════════════════════════════════════════════════════════════
    //  Response parsing
    // ════════════════════════════════════════════════════════════════

    private List<WritingEvaluateResponse.ErrorDto> parseResponse(String body, String text) {
        if (body == null || body.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(body);

            if (!root.path("status").asBoolean(false)) {
                String msg = root.path("message").asText("");
                log.warn("Trinka API error: {}", msg);
                return List.of();
            }

            JsonNode responseArr = root.path("response");
            if (!responseArr.isArray()) return List.of();

            List<WritingEvaluateResponse.ErrorDto> errors = new ArrayList<>();
            int idx = 1;

            for (JsonNode sentenceNode : responseArr) {
                // sentence start_index is the offset within the full paragraph
                int sentenceStart = sentenceNode.path("start_index").asInt(0);

                JsonNode results = sentenceNode.path("sentence_result");
                if (!results.isArray()) continue;

                for (JsonNode result : results) {
                    WritingEvaluateResponse.ErrorDto dto = toErrorDto(result, text, sentenceStart, idx++);
                    if (dto != null) {
                        errors.add(dto);
                    }
                }
            }
            return errors;
        } catch (Exception e) {
            log.warn("Trinka response parse failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static final int MAX_SPAN_LENGTH = 80;

    private WritingEvaluateResponse.ErrorDto toErrorDto(JsonNode result, String text, int sentenceStart, int idx) {
        int localStart = result.path("start_index").asInt(0);
        int localEnd = result.path("end_index").asInt(0);
        int start = sentenceStart + localStart;
        int end = sentenceStart + localEnd;

        if (start < 0 || end <= start || end > text.length()) return null;
        if ((end - start) > MAX_SPAN_LENGTH) return null;

        String coveredText = result.path("covered_text").asText("");
        if (coveredText.isBlank()) return null;

        // Get first suggestion from output array
        JsonNode outputArr = result.path("output");
        if (!outputArr.isArray() || outputArr.isEmpty()) return null;

        JsonNode firstSuggestion = outputArr.get(0);
        String revisedText = firstSuggestion.path("revised_text").asText("");
        String comment = firstSuggestion.path("comment").asText("");
        int type = firstSuggestion.path("type").asInt(1);
        String errorCategory = firstSuggestion.path("error_category").asText("");
        boolean ctaPresent = firstSuggestion.path("cta_present").asBoolean(true);

        // Skip non-actionable suggestions
        if (!ctaPresent) return null;

        // Skip enhancements (type=4) and style (type=5), keep grammar(1), spelling(2), advisor(3)
        if (type == 4 || type == 5) return null;

        // Skip if suggestion is same as original
        if (revisedText.equals(coveredText)) return null;

        // Check critical_error from category_details_v2
        JsonNode categoryDetails = result.path("category_details_v2");
        boolean critical = categoryDetails.path("critical_error").asBoolean(false);

        WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
        dto.setId("tk" + idx);
        dto.setEngine("trinka");
        dto.setType(mapType(type, errorCategory));
        dto.setCategory("error");
        dto.setSeverity(critical ? "major" : mapSeverity(type));
        dto.setSpan(new WritingEvaluateResponse.SpanDto(start, end));
        dto.setOriginal(coveredText);
        dto.setSuggestion(revisedText);
        dto.setReason(buildReason(comment, errorCategory));

        // lang_category from category_details_v2, mapped to Chinese
        String langCategory = categoryDetails.path("lang_category").asText("");
        if (!langCategory.isBlank()) {
            dto.setLangCategory(mapLangCategoryToChinese(langCategory));
        } else if (!errorCategory.isBlank()) {
            dto.setLangCategory(mapLangCategoryToChinese(errorCategory));
        }

        // Collect all alternatives from output array
        List<String> alternatives = new ArrayList<>();
        for (JsonNode out : outputArr) {
            String alt = out.path("revised_text").asText("");
            if (!alt.isBlank() && !alt.equals(coveredText) && !alternatives.contains(alt)) {
                alternatives.add(alt);
            }
        }
        if (alternatives.size() > 1) {
            dto.setAlternatives(alternatives);
        }

        return dto;
    }

    // ════════════════════════════════════════════════════════════════
    //  Mapping helpers
    // ════════════════════════════════════════════════════════════════

    private String mapType(int type, String errorCategory) {
        String cat = errorCategory.toLowerCase();

        if (cat.contains("singular") || cat.contains("plural")) return "morphology";
        if (cat.contains("article")) return "article";
        if (cat.contains("preposition")) return "preposition";
        if (cat.contains("subject") && cat.contains("verb")) return "subject_verb";
        if (cat.contains("tense")) return "tense";
        if (cat.contains("syntax")) return "syntax";
        if (cat.contains("punctuation")) return "punctuation";
        if (cat.contains("spell")) return "spelling";

        return switch (type) {
            case 1 -> "syntax";       // grammar
            case 2 -> "spelling";
            case 3 -> "word_choice";  // advisor
            default -> "syntax";
        };
    }

    private String mapSeverity(int type) {
        return switch (type) {
            case 1, 2 -> "major";   // grammar, spelling
            default -> "minor";
        };
    }

    private String buildReason(String comment, String errorCategory) {
        if (comment != null && !comment.isBlank()) {
            return comment;
        }
        if (errorCategory != null && !errorCategory.isBlank()) {
            return errorCategory;
        }
        return "语法错误";
    }

    /**
     * Trinka lang_category → 中文映射（完整覆盖 Appendix 2 所有分类）
     */
    private static final Map<String, String> LANG_CATEGORY_CN;
    static {
        Map<String, String> m = new java.util.LinkedHashMap<>();

        // ══ Correctness → Grammar ══
        m.put("Articles", "冠词");
        m.put("Conjunctions", "连词");
        m.put("Prepositions", "介词");
        m.put("Pronouns & Determiners", "代词/限定词");
        m.put("Singular-Plural Nouns", "单复数");
        m.put("Singular-Plural nouns", "单复数");
        m.put("Subject-Verb Agreement", "主谓一致");
        m.put("Tense", "时态");
        m.put("Verbs", "动词");
        m.put("Verb Forms", "动词形式");
        m.put("Word Form", "词形");
        m.put("Adjectives/Adverbs", "形容词/副词");
        m.put("Adjective Forms", "形容词形式");
        m.put("Adverb Forms", "副词形式");
        m.put("Modal Verbs", "情态动词");
        m.put("Gerunds & Infinitives", "动名词/不定式");
        m.put("Conditional Sentences", "条件句");
        m.put("Relative Clauses", "定语从句");
        m.put("Reported Speech", "间接引语");
        m.put("Double Negatives", "双重否定");
        m.put("Comparatives & Superlatives", "比较级/最高级");
        m.put("Noun Forms", "名词形式");
        m.put("Pronoun Reference", "代词指代");
        m.put("Modifiers", "修饰语");

        // ══ Correctness → Spelling ══
        m.put("Spellings", "拼写");
        m.put("Spelling", "拼写");
        m.put("Spellings & Typos", "拼写/错字");

        // ══ Correctness → Punctuation ══
        m.put("Punctuation", "标点");
        m.put("Comma Usage", "逗号用法");
        m.put("Hyphenation", "连字符");
        m.put("Apostrophe", "撇号");

        // ══ Correctness → Syntax & Vocabulary ══
        m.put("Syntax", "句法");
        m.put("Other Errors", "其他错误");
        m.put("Accurate Phrasing", "精确措辞");
        m.put("Syntax & Vocabulary", "句法/词汇");
        m.put("Run-on Sentences", "连写句");
        m.put("Sentence Fragments", "句子残缺");
        m.put("Parallelism", "平行结构");
        m.put("Word Order", "语序");
        m.put("Sentence Structure", "句子结构");

        // ══ Clarity ══
        m.put("Clarity", "清晰度");
        m.put("Word Choice", "用词");
        m.put("Word choice", "用词");
        m.put("Brevity", "简洁");
        m.put("Vague Words/Phrases", "模糊表达");
        m.put("Vague Language", "模糊表达");
        m.put("Hedge Words", "模糊词");
        m.put("Idioms/Clichés", "习语/陈词");
        m.put("Idioms", "习语");
        m.put("Ambiguity", "歧义");

        // ══ Fluency ══
        m.put("Fluency", "流畅度");
        m.put("Redundancy", "冗余");
        m.put("Noun Stacks", "名词堆砌");
        m.put("Plain Language", "通俗表达");
        m.put("Enhancement", "表达优化");
        m.put("Active/Passive Voice", "主动/被动语态");
        m.put("Passive Voice", "被动语态");
        m.put("Wordiness", "累赘");
        m.put("Conciseness", "简洁性");

        // ══ Style ══
        m.put("Style", "风格");
        m.put("Capitalization & Spacing", "大小写/间距");
        m.put("Number Style", "数字格式");
        m.put("Symbols/Notations", "符号/标记");
        m.put("Regional Style", "地区风格");
        m.put("Contractions", "缩写");
        m.put("Formal Word/Phrase Choice", "正式用词");
        m.put("Other Style", "其他风格");
        m.put("Consistency", "一致性");
        m.put("Formality", "正式度");
        m.put("Tone", "语气");
        m.put("Collocation", "搭配");

        // ══ Style Guide Compliance ══
        m.put("Style Guide Compliance", "风格规范");
        m.put("APA", "APA规范");
        m.put("AMA", "AMA规范");
        m.put("IEEE", "IEEE规范");
        m.put("AGU", "AGU规范");

        // ══ Inclusivity ══
        m.put("Inclusivity", "包容性");
        m.put("Nationality Bias", "国籍偏见");

        // ══ Top-level / fallback ══
        m.put("Correctness", "正确性");
        m.put("Grammar", "语法");
        m.put("Other", "其他");

        LANG_CATEGORY_CN = Map.copyOf(m);
    }

    private String mapLangCategoryToChinese(String category) {
        if (category == null || category.isBlank()) return "语法";
        String cn = LANG_CATEGORY_CN.get(category);
        if (cn != null) return cn;
        // Case-insensitive fallback
        for (var entry : LANG_CATEGORY_CN.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(category)) return entry.getValue();
        }
        // Log unmapped category for future addition
        log.debug("Trinka unmapped lang_category: {}", category);
        return category;
    }
}


