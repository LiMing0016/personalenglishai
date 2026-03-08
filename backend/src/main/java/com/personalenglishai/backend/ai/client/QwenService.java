package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里千问（Qwen）独立服务，使用 OpenAI 兼容接口格式。
 * 用于轻量任务（题目解析等），与 OpenAiClient 完全独立。
 */
@Service
public class QwenService {

    private static final Logger log = LoggerFactory.getLogger(QwenService.class);

    private final WebClient webClient;
    private final String model;
    private final String vlModel;
    private final long timeoutMs;
    private final boolean promptDebugEnabled;
    private final boolean promptRawLogEnabled;
    private final int promptRawLogMaxChars;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;

    public QwenService(
            @Value("${qwen.api-key:}") String apiKey,
            @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Value("${qwen.model:qwen-turbo}") String model,
            @Value("${qwen.vl-model:qwen-vl-plus}") String vlModel,
            @Value("${qwen.timeout-ms:15000}") long timeoutMs,
            @Value("${qwen.prompt.debug:false}") boolean promptDebugEnabled,
            @Value("${qwen.prompt.log-raw-enabled:false}") boolean promptRawLogEnabled,
            @Value("${qwen.prompt.log-raw-max-chars:12000}") int promptRawLogMaxChars) {
        this.model = model;
        this.vlModel = vlModel;
        this.timeoutMs = timeoutMs;
        this.promptDebugEnabled = promptDebugEnabled;
        this.promptRawLogEnabled = promptRawLogEnabled;
        this.promptRawLogMaxChars = Math.max(2000, promptRawLogMaxChars);
        this.enabled = apiKey != null && !apiKey.isBlank();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
                .build();

        if (this.enabled) {
            log.info("[QWEN] initialized: baseUrl={}, model={}, vlModel={}, timeoutMs={}, promptDebugEnabled={}, promptRawLogEnabled={}",
                    baseUrl, model, vlModel, timeoutMs, promptDebugEnabled, promptRawLogEnabled);
        } else {
            log.warn("[QWEN] api-key not configured, service disabled");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 调用千问 chat completions 接口。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return AI 回复的文本内容
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, 0.3, 500);
    }

    public String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        if (!enabled) {
            throw new IllegalStateException("Qwen service is not configured (missing api-key)");
        }

        String traceId = newTraceId();
        long startTime = System.currentTimeMillis();

        String safeSystemPrompt = systemPrompt == null ? "" : systemPrompt;
        String safeUserPrompt = userPrompt == null ? "" : userPrompt;

        var messages = List.of(
                Map.of("role", "system", "content", safeSystemPrompt),
                Map.of("role", "user", "content", safeUserPrompt)
        );

        var requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens
        );

        logPromptSummary(traceId, "chat_completions", model, safeSystemPrompt, safeUserPrompt, payloadBytes(requestBody));

        try {
            String responseBody = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[QWEN] empty response traceId={} latencyMs={}", traceId, System.currentTimeMillis() - startTime);
                return null;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText(null);
                logResponseSummary(traceId, "chat_completions", model, root, content, startTime);
                return content;
            }

            log.warn("[QWEN] no choices in response traceId={} latencyMs={} responsePreview={}",
                    traceId,
                    System.currentTimeMillis() - startTime,
                    previewForLog(responseBody, 300));
            return null;
        } catch (Exception e) {
            log.error("[QWEN] call failed traceId={} endpoint=chat_completions model={} latencyMs={} message={}",
                    traceId, model, System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new RuntimeException("Qwen API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 调用千问 VL 视觉模型，识别图片内容。
     *
     * @param systemPrompt 系统提示词
     * @param textPrompt   文本提示
     * @param imageBase64  图片的 base64 编码（不含 data:image 前缀）
     * @param mimeType     图片 MIME 类型（如 image/png）
     * @return AI 回复的文本内容
     */
    public String visionChat(String systemPrompt, String textPrompt, String imageBase64, String mimeType) {
        if (!enabled) {
            throw new IllegalStateException("Qwen service is not configured (missing api-key)");
        }

        String traceId = newTraceId();
        long startTime = System.currentTimeMillis();
        String dataUrl = "data:" + mimeType + ";base64," + imageBase64;
        String safeSystemPrompt = systemPrompt == null ? "" : systemPrompt;
        String safeTextPrompt = textPrompt == null ? "" : textPrompt;

        var userContent = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                Map.of("type", "text", "text", safeTextPrompt)
        );

        var messages = List.of(
                Map.of("role", "system", "content", safeSystemPrompt),
                Map.<String, Object>of("role", "user", "content", userContent)
        );

        var requestBody = Map.of(
                "model", vlModel,
                "messages", messages,
                "temperature", 0.2,
                "max_tokens", 1000
        );

        logPromptSummary(traceId, "vision_chat_completions", vlModel, safeSystemPrompt, safeTextPrompt, payloadBytes(requestBody));
        log.info("[QWEN-VL] request traceId={} model={} imageBase64Len={} imageSha256={}",
                traceId, vlModel, imageBase64.length(), sha256Hex(imageBase64));

        try {
            String responseBody = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, 30000)))
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[QWEN-VL] empty response traceId={} latencyMs={}", traceId, System.currentTimeMillis() - startTime);
                return null;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText(null);
                logResponseSummary(traceId, "vision_chat_completions", vlModel, root, content, startTime);
                return content;
            }

            log.warn("[QWEN-VL] no choices in response traceId={} latencyMs={} responsePreview={}",
                    traceId,
                    System.currentTimeMillis() - startTime,
                    previewForLog(responseBody, 300));
            return null;
        } catch (Exception e) {
            log.error("[QWEN-VL] call failed traceId={} model={} latencyMs={} message={}",
                    traceId, vlModel, System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw new RuntimeException("Qwen VL API call failed: " + e.getMessage(), e);
        }
    }

    private void logPromptSummary(String traceId,
                                  String endpoint,
                                  String targetModel,
                                  String systemPrompt,
                                  String userPrompt,
                                  int payloadBytes) {
        log.info("[QWEN] prompt traceId={} endpoint={} model={} payloadBytes={} systemPromptLength={} userPromptLength={} systemPromptSha256={} userPromptSha256={}",
                traceId,
                endpoint,
                targetModel,
                payloadBytes,
                systemPrompt.length(),
                userPrompt.length(),
                sha256Hex(systemPrompt),
                sha256Hex(userPrompt));
        if (promptDebugEnabled) {
            log.debug("[QWEN] prompt preview traceId={} role=system content={}", traceId, previewForLog(systemPrompt, 160));
            log.debug("[QWEN] prompt preview traceId={} role=user content={}", traceId, previewForLog(userPrompt, 160));
        }
        if (promptRawLogEnabled) {
            log.info("[QWEN] prompt raw traceId={} role=system content=\n{}", traceId, limitForRawLog(redactForLog(systemPrompt)));
            log.info("[QWEN] prompt raw traceId={} role=user content=\n{}", traceId, limitForRawLog(redactForLog(userPrompt)));
        }
    }

    private void logResponseSummary(String traceId,
                                    String endpoint,
                                    String targetModel,
                                    JsonNode root,
                                    String content,
                                    long startTime) {
        JsonNode usage = root.path("usage");
        String requestId = nullIfBlank(root.path("id").asText(null));
        int inputTokens = intOrDefault(usage.path("prompt_tokens"), intOrDefault(usage.path("input_tokens"), -1));
        int outputTokens = intOrDefault(usage.path("completion_tokens"), intOrDefault(usage.path("output_tokens"), -1));
        int totalTokens = intOrDefault(usage.path("total_tokens"), -1);
        long latencyMs = System.currentTimeMillis() - startTime;
        int contentLength = content == null ? 0 : content.length();

        log.info("[QWEN] response traceId={} endpoint={} model={} requestId={} latencyMs={} inputTokens={} outputTokens={} totalTokens={} contentLength={}",
                traceId,
                endpoint,
                targetModel,
                requestId != null ? requestId : "",
                latencyMs,
                inputTokens,
                outputTokens,
                totalTokens,
                contentLength);
        if (promptDebugEnabled && content != null) {
            log.debug("[QWEN] response preview traceId={} content={}", traceId, previewForLog(content, 200));
        }
    }

    private String newTraceId() {
        return UUID.randomUUID().toString();
    }

    private int payloadBytes(Object requestBody) {
        try {
            return objectMapper.writeValueAsBytes(requestBody).length;
        } catch (Exception e) {
            return 0;
        }
    }

    private int intOrDefault(JsonNode node, int defaultValue) {
        return node != null && node.isNumber() ? node.asInt() : defaultValue;
    }

    private String previewForLog(String content, int maxLen) {
        String normalized = redactForLog(content).replace("\r\n", "\n").replace("\n", "\\n");
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    private String redactForLog(String content) {
        if (content == null) {
            return "";
        }
        String redacted = content;
        redacted = redacted.replaceAll("sk-[a-zA-Z0-9]+", "sk-***");
        redacted = redacted.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        return redacted;
    }

    private String limitForRawLog(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= promptRawLogMaxChars) {
            return content;
        }
        return content.substring(0, promptRawLogMaxChars)
                + "\n...[truncated, totalChars=" + content.length() + "]";
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
