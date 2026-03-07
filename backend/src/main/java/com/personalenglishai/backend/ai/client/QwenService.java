package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;

    public QwenService(
            @Value("${qwen.api-key:}") String apiKey,
            @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
            @Value("${qwen.model:qwen-turbo}") String model,
            @Value("${qwen.vl-model:qwen-vl-plus}") String vlModel,
            @Value("${qwen.timeout-ms:15000}") long timeoutMs) {
        this.model = model;
        this.vlModel = vlModel;
        this.timeoutMs = timeoutMs;
        this.enabled = apiKey != null && !apiKey.isBlank();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
                .build();

        if (this.enabled) {
            log.info("[QWEN] initialized: baseUrl={}, model={}, timeoutMs={}", baseUrl, model, timeoutMs);
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

        var messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        );

        var requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens
        );

        log.debug("[QWEN] request: model={}, userPromptLen={}", model, userPrompt.length());

        try {
            String responseBody = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[QWEN] empty response");
                return null;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText(null);
                log.debug("[QWEN] response ok, contentLen={}", content != null ? content.length() : 0);
                return content;
            }

            log.warn("[QWEN] no choices in response: {}", responseBody);
            return null;
        } catch (Exception e) {
            log.error("[QWEN] call failed: {}", e.getMessage(), e);
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

        String dataUrl = "data:" + mimeType + ";base64," + imageBase64;

        // 用户消息：图片 + 文字（OpenAI vision 格式）
        var userContent = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                Map.of("type", "text", "text", textPrompt)
        );

        var messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.<String, Object>of("role", "user", "content", userContent)
        );

        var requestBody = Map.of(
                "model", vlModel,
                "messages", messages,
                "temperature", 0.2,
                "max_tokens", 1000
        );

        log.info("[QWEN-VL] request: vlModel={}, imageBase64Len={}", vlModel, imageBase64.length());

        try {
            String responseBody = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(Math.max(timeoutMs, 30000)))
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("[QWEN-VL] empty response");
                return null;
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText(null);
                log.info("[QWEN-VL] response ok, contentLen={}", content != null ? content.length() : 0);
                return content;
            }

            log.warn("[QWEN-VL] no choices in response: {}", responseBody);
            return null;
        } catch (Exception e) {
            log.error("[QWEN-VL] call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Qwen VL API call failed: " + e.getMessage(), e);
        }
    }
}
