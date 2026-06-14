package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "paddle")
public class PaddleTranslationOcrService implements TranslationOcrService {
    private final boolean enabled;
    private final String baseUrl;
    private final String endpoint;
    private final String language;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PaddleTranslationOcrService(
            @Value("${app.ocr.paddle.enabled:true}") boolean enabled,
            @Value("${app.ocr.paddle.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${app.ocr.paddle.endpoint:/ocr/pdf}") String endpoint,
            @Value("${app.ocr.paddle.language:ch,eng}") String language,
            @Value("${app.ocr.paddle.timeout-ms:60000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.endpoint = normalizeEndpoint(endpoint);
        this.language = language;
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, timeoutMs)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public TranslationOcrResult recognizePdf(byte[] pdfBytes) {
        if (!enabled) {
            return TranslationOcrResult.unavailable("PaddleOCR provider is disabled");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            return TranslationOcrResult.failed("OCR 输入 PDF 为空");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(pdfBytes));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TranslationOcrResult.unavailable("PaddleOCR 服务返回异常状态: " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (IOException e) {
            return TranslationOcrResult.unavailable("PaddleOCR 服务不可用: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TranslationOcrResult.failed("PaddleOCR 识别被中断");
        } catch (RuntimeException e) {
            return TranslationOcrResult.failed("PaddleOCR 响应解析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRequest(byte[] pdfBytes) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("documentBase64", Base64.getEncoder().encodeToString(pdfBytes));
        request.put("language", language);
        return request;
    }

    private TranslationOcrResult parseResponse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("status").asText("").equalsIgnoreCase("FAILED")) {
            return TranslationOcrResult.failed(root.path("message").asText("PaddleOCR 识别失败"));
        }

        JsonNode pagesNode = root.path("pages");
        if (!pagesNode.isArray()) {
            return TranslationOcrResult.failed("PaddleOCR 响应缺少 pages 数组");
        }

        List<TranslationOcrPageText> pages = new ArrayList<>();
        for (JsonNode pageNode : pagesNode) {
            int pageNumber = pageNode.path("pageNumber").asInt(pages.size() + 1);
            String text = pageNode.path("text").asText("");
            if (!text.isBlank()) {
                pages.add(new TranslationOcrPageText(pageNumber, text));
            }
        }
        if (pages.isEmpty()) {
            return TranslationOcrResult.failed(root.path("message").asText("PaddleOCR 未识别到有效文本"));
        }
        return TranslationOcrResult.succeeded(pages);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizeEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return "/ocr/pdf";
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
