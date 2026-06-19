package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final String parseMode;
    private final int maxPages;
    private final int dpi;
    private final boolean enableLayout;
    private final boolean enableTable;
    private final boolean enableFormula;
    private final boolean enableOrientation;
    private final boolean enableUnwarping;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PaddleTranslationOcrService(
            @Value("${app.ocr.paddle.enabled:true}") boolean enabled,
            @Value("${app.ocr.paddle.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${app.ocr.paddle.endpoint:/ocr/pdf}") String endpoint,
            @Value("${app.ocr.paddle.language:ch,eng}") String language,
            @Value("${app.ocr.paddle.timeout-ms:60000}") long timeoutMs,
            @Value("${app.ocr.paddle.parse-mode:standard}") String parseMode,
            @Value("${app.ocr.paddle.max-pages:500}") int maxPages,
            @Value("${app.ocr.paddle.dpi:220}") int dpi,
            @Value("${app.ocr.paddle.enable-layout:false}") boolean enableLayout,
            @Value("${app.ocr.paddle.enable-table:false}") boolean enableTable,
            @Value("${app.ocr.paddle.enable-formula:false}") boolean enableFormula,
            @Value("${app.ocr.paddle.enable-orientation:true}") boolean enableOrientation,
            @Value("${app.ocr.paddle.enable-unwarping:false}") boolean enableUnwarping,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.endpoint = normalizeEndpoint(endpoint);
        this.language = language;
        this.parseMode = normalizeParseMode(parseMode);
        this.maxPages = Math.max(1, Math.min(500, maxPages));
        this.dpi = Math.max(72, Math.min(400, dpi));
        this.enableLayout = enableLayout;
        this.enableTable = enableTable;
        this.enableFormula = enableFormula;
        this.enableOrientation = enableOrientation;
        this.enableUnwarping = enableUnwarping;
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, timeoutMs)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public PaddleTranslationOcrService(
            boolean enabled,
            String baseUrl,
            String endpoint,
            String language,
            long timeoutMs,
            ObjectMapper objectMapper) {
        this(
                enabled,
                baseUrl,
                endpoint,
                language,
                timeoutMs,
                "standard",
                500,
                220,
                false,
                false,
                false,
                true,
                false,
                objectMapper
        );
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
        request.put("parseMode", parseMode);
        request.put("maxPages", maxPages);
        request.put("dpi", dpi);
        request.put("enableLayout", enableLayout);
        request.put("enableTable", enableTable);
        request.put("enableFormula", enableFormula);
        request.put("enableOrientation", enableOrientation);
        request.put("enableUnwarping", enableUnwarping);
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
            String text = extractPageText(pageNode);
            List<TranslationOcrElement> elements = extractElements(pageNode);
            if (text.isBlank() && !elements.isEmpty()) {
                text = joinElementText(elements);
            }
            if (!text.isBlank() || !elements.isEmpty()) {
                pages.add(new TranslationOcrPageText(
                        pageNumber,
                        text,
                        elements,
                        extractStringList(pageNode.path("warnings")),
                        nullableInt(pageNode, "width"),
                        nullableInt(pageNode, "height"),
                        nullableDouble(pageNode, "confidence"),
                        pageNode.path("rawText").asText(null),
                        pageNode.path("cleanedText").asText(null)
                ));
            }
        }
        if (pages.isEmpty()) {
            return TranslationOcrResult.failed(root.path("message").asText("PaddleOCR 未识别到有效文本"));
        }
        return TranslationOcrResult.succeeded(pages, body);
    }

    private String extractPageText(JsonNode pageNode) {
        String explicitText = firstNonBlank(pageNode.path("cleanedText").asText(""), pageNode.path("text").asText(""));
        List<String> lines = new ArrayList<>();
        if (!explicitText.isBlank()) {
            lines.add(explicitText);
        } else {
            JsonNode elementsNode = pageNode.path("elements");
            if (elementsNode.isArray()) {
                for (JsonNode elementNode : elementsNode) {
                    String elementText = elementNode.path("text").asText("");
                    if (!elementText.isBlank()) {
                        lines.add(elementText);
                    }
                }
            }
            if (lines.isEmpty()) {
                JsonNode blocksNode = pageNode.path("blocks");
                if (blocksNode.isArray()) {
                    for (JsonNode blockNode : blocksNode) {
                        String blockText = blockNode.path("text").asText("");
                        if (!blockText.isBlank()) {
                            lines.add(blockText);
                        }
                    }
                }
            }
        }

        JsonNode formulasNode = pageNode.path("formulas");
        if (formulasNode.isArray()) {
            for (JsonNode formulaNode : formulasNode) {
                String latex = formulaNode.path("latex").asText("");
                String placeholder = "[FORMULA: " + latex + "]";
                String joined = String.join("\n", lines);
                if (!latex.isBlank() && !joined.contains(latex) && !joined.contains(placeholder)) {
                    lines.add(placeholder);
                }
            }
        }

        return String.join("\n", lines).trim();
    }

    private List<TranslationOcrElement> extractElements(JsonNode pageNode) {
        List<TranslationOcrElement> elements = new ArrayList<>();
        JsonNode elementsNode = pageNode.path("elements");
        if (elementsNode.isArray()) {
            for (JsonNode elementNode : elementsNode) {
                String text = elementNode.path("text").asText("");
                if (text.isBlank()) {
                    continue;
                }
                elements.add(new TranslationOcrElement(
                        elementNode.path("type").asText("paragraph"),
                        text,
                        jsonStringOrNull(elementNode.path("bbox")),
                        nullableDouble(elementNode, "confidence"),
                        elementNode.path("order").asInt(elements.size() + 1),
                        elementNode.path("source").asText("paddle_ocr"),
                        elementNode.path("rawType").asText(null),
                        extractStringList(elementNode.path("warnings"))
                ));
            }
        }
        if (elements.isEmpty()) {
            JsonNode blocksNode = pageNode.path("blocks");
            if (blocksNode.isArray()) {
                for (JsonNode blockNode : blocksNode) {
                    String text = blockNode.path("text").asText("");
                    if (text.isBlank()) {
                        continue;
                    }
                    elements.add(new TranslationOcrElement(
                            "paragraph",
                            text,
                            jsonStringOrNull(blockNode.path("bbox")),
                            nullableDouble(blockNode, "confidence"),
                            blockNode.path("order").asInt(elements.size() + 1),
                            "paddle_ocr",
                            "text",
                            List.of()
                    ));
                }
            }
        }
        JsonNode formulasNode = pageNode.path("formulas");
        if (formulasNode.isArray()) {
            for (JsonNode formulaNode : formulasNode) {
                String latex = formulaNode.path("latex").asText("");
                if (latex.isBlank()) {
                    continue;
                }
                elements.add(new TranslationOcrElement(
                        "formula",
                        latex,
                        jsonStringOrNull(formulaNode.path("bbox")),
                        nullableDouble(formulaNode, "confidence"),
                        elements.size() + 1,
                        "paddle_ocr_formula",
                        "formula",
                        extractStringList(formulaNode.path("warnings"))
                ));
            }
        }
        return elements;
    }

    private String joinElementText(List<TranslationOcrElement> elements) {
        List<String> lines = new ArrayList<>();
        for (TranslationOcrElement element : elements) {
            if (element.getText() != null && !element.getText().isBlank()) {
                if ("formula".equals(element.getType())) {
                    lines.add("[FORMULA: " + element.getText() + "]");
                } else {
                    lines.add(element.getText());
                }
            }
        }
        return String.join("\n", lines).trim();
    }

    private List<String> extractStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private Double nullableDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private String jsonStringOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.toString();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
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

    private static String normalizeParseMode(String value) {
        if ("high_quality".equalsIgnoreCase(value)) {
            return "high_quality";
        }
        return "standard";
    }
}
