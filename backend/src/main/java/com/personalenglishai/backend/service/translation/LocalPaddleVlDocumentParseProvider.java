package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAssetDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.document-parse.local-paddle-vl.enabled", havingValue = "true")
public class LocalPaddleVlDocumentParseProvider implements DocumentParseProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalPaddleVlDocumentParseProvider.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String endpoint;
    private final String language;
    private final Duration timeout;
    private final int maxPages;
    private final int dpi;
    private final boolean enableLayout;
    private final boolean enableTable;
    private final boolean enableFormula;
    private final boolean enableOrientation;
    private final boolean enableUnwarping;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public LocalPaddleVlDocumentParseProvider(
            @Value("${app.document-parse.local-paddle-vl.enabled:false}") boolean enabled,
            @Value("${app.document-parse.local-paddle-vl.base-url:http://host.docker.internal:8091}") String baseUrl,
            @Value("${app.document-parse.local-paddle-vl.endpoint:/vl/pdf}") String endpoint,
            @Value("${app.document-parse.local-paddle-vl.language:ch,eng}") String language,
            @Value("${app.document-parse.local-paddle-vl.timeout-ms:300000}") long timeoutMs,
            @Value("${app.document-parse.local-paddle-vl.max-pages:20}") int maxPages,
            @Value("${app.document-parse.local-paddle-vl.dpi:220}") int dpi,
            @Value("${app.document-parse.local-paddle-vl.enable-layout:true}") boolean enableLayout,
            @Value("${app.document-parse.local-paddle-vl.enable-table:true}") boolean enableTable,
            @Value("${app.document-parse.local-paddle-vl.enable-formula:false}") boolean enableFormula,
            @Value("${app.document-parse.local-paddle-vl.enable-orientation:false}") boolean enableOrientation,
            @Value("${app.document-parse.local-paddle-vl.enable-unwarping:false}") boolean enableUnwarping,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.endpoint = normalizeEndpoint(endpoint);
        this.language = language == null || language.isBlank() ? "ch,eng" : language;
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        this.maxPages = Math.max(1, Math.min(500, maxPages));
        this.dpi = Math.max(72, Math.min(400, dpi));
        this.enableLayout = enableLayout;
        this.enableTable = enableTable;
        this.enableFormula = enableFormula;
        this.enableOrientation = enableOrientation;
        this.enableUnwarping = enableUnwarping;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return enabled
                && request != null
                && request.parseMode() == DocumentParseMode.HIGH_QUALITY
                && ("PDF".equalsIgnoreCase(request.fileType())
                || TranslationDocumentFileTypes.hasExtension(request.originalFilename(), "pdf")
                || contentTypeContains(request.contentType(), "pdf"));
    }

    @Override
    public DocumentParseProviderType providerType() {
        return DocumentParseProviderType.LOCAL_PADDLE_VL;
    }

    @Override
    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        if (!supports(request)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "本地 PaddleOCR-VL 未启用或不支持该文件");
        }
        if (request.bytes() == null || request.bytes().length == 0) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "PDF 文件不能为空");
        }

        long startedAt = System.currentTimeMillis();
        try {
            log.info(
                    "[document-parse] local paddle-vl request endpoint={} pageStart={} pageEnd={} maxPages={} dpi={} layout={} table={} formula={} orientation={} unwarping={} bytes={}",
                    endpoint,
                    effectivePageStart(request),
                    request.pageEnd(),
                    effectiveMaxPages(request),
                    dpi,
                    enableLayout,
                    enableTable,
                    enableFormula,
                    enableOrientation,
                    enableUnwarping,
                    request.bytes().length
            );
            String requestBody = objectMapper.writeValueAsString(buildRequest(request));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[document-parse] local paddle-vl response httpStatus={} elapsedMs={}",
                        response.statusCode(),
                        Math.max(0, System.currentTimeMillis() - startedAt));
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "本地 PaddleOCR-VL 服务返回异常状态: " + response.statusCode());
            }
            TranslationDocumentParseResponse parsed = parseResponse(request.originalFilename(), response.body());
            log.info("[document-parse] local paddle-vl response status={} pages={} blocks={} elapsedMs={}",
                    parsed.getParseStatus(),
                    parsed.getPageCount(),
                    parsed.getBlockCount(),
                    Math.max(0, System.currentTimeMillis() - startedAt));
            return parsed;
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "本地 PaddleOCR-VL 服务不可用: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "本地 PaddleOCR-VL 解析被中断");
        }
    }

    private Map<String, Object> buildRequest(DocumentParseRequest parseRequest) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("documentBase64", Base64.getEncoder().encodeToString(parseRequest.bytes()));
        request.put("language", language);
        request.put("parseMode", "high_quality");
        request.put("pageStart", effectivePageStart(parseRequest));
        if (parseRequest.pageEnd() != null) {
            request.put("pageEnd", parseRequest.pageEnd());
        }
        request.put("maxPages", effectiveMaxPages(parseRequest));
        request.put("dpi", dpi);
        request.put("enableTextOcr", true);
        request.put("enableLayout", enableLayout);
        request.put("enableTable", enableTable);
        request.put("enableFormula", enableFormula);
        request.put("enableOrientation", enableOrientation);
        request.put("enableUnwarping", enableUnwarping);
        return request;
    }

    private int effectivePageStart(DocumentParseRequest request) {
        return request.pageStart() == null ? 1 : Math.max(1, request.pageStart());
    }

    private int effectiveMaxPages(DocumentParseRequest request) {
        if (request.maxPages() == null) {
            return maxPages;
        }
        return Math.max(1, Math.min(maxPages, request.maxPages()));
    }

    private TranslationDocumentParseResponse parseResponse(String originalFilename, String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root.path("status").asText("").equalsIgnoreCase("FAILED")) {
            throw new BizException(
                    ErrorCode.COMMON_VALIDATION_ERROR,
                    root.path("message").asText("本地 PaddleOCR-VL 解析失败")
            );
        }

        JsonNode pagesNode = root.path("pages");
        if (!pagesNode.isArray()) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "本地 PaddleOCR-VL 响应缺少 pages 数组");
        }

        List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
        List<TranslationDocumentElementDto> elements = new ArrayList<>();
        List<TranslationDocumentAssetDto> assets = extractAssets(root.path("assets"));
        List<String> warnings = new ArrayList<>();
        warnings.add("已使用本地 PaddleOCR-VL 结果生成精读材料。");
        warnings.addAll(extractStringList(root.path("warnings")));

        int maxPageNumber = Math.max(0, root.path("pageCount").asInt(0));
        int blockOrder = 1;
        int elementOrder = 1;
        for (JsonNode pageNode : pagesNode) {
            int pageNumber = pageNode.path("pageNumber").asInt(maxPageNumber + 1);
            maxPageNumber = Math.max(maxPageNumber, pageNumber);
            warnings.addAll(extractStringList(pageNode.path("warnings")));

            String pageText = extractPageText(pageNode);
            if (!pageText.isBlank()) {
                blocks.add(new TranslationDocumentBlockDto(
                        "p" + pageNumber + "-vl-b" + blockOrder,
                        inferBlockType(pageText),
                        blockOrder,
                        pageNumber,
                        pageText,
                        null
                ));
                blockOrder++;
            }

            JsonNode elementsNode = pageNode.path("elements");
            if (elementsNode.isArray()) {
                for (JsonNode elementNode : elementsNode) {
                    String text = elementNode.path("text").asText("");
                    if (text.isBlank()) {
                        continue;
                    }
                    TranslationDocumentElementDto element = new TranslationDocumentElementDto();
                    element.setId("p" + pageNumber + "-vl-e" + elementOrder);
                    element.setType(normalizeElementType(elementNode.path("type").asText("paragraph")));
                    element.setOrder(elementOrder);
                    element.setPageNumber(pageNumber);
                    element.setText(text);
                    element.setBbox(jsonStringOrNull(elementNode.path("bbox")));
                    element.setProvider(elementNode.path("source").asText("paddle_vl"));
                    element.setConfidence(nullableDouble(elementNode, "confidence"));
                    element.setRecognitionStatus("READY");
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("source", elementNode.path("source").asText("paddle_vl"));
                    metadata.put("rawType", elementNode.path("rawType").asText(null));
                    metadata.put("warnings", extractStringList(elementNode.path("warnings")));
                    metadata.put("pageWidth", nullableInt(pageNode, "width"));
                    metadata.put("pageHeight", nullableInt(pageNode, "height"));
                    element.setMetadata(metadata);
                    elements.add(element);
                    warnings.addAll(extractStringList(elementNode.path("warnings")));
                    elementOrder++;
                }
            }
        }

        if (blocks.isEmpty() && elements.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "本地 PaddleOCR-VL 未解析出可用文本");
        }

        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId(UUID.randomUUID().toString());
        response.setFileName(originalFilename);
        response.setSourceType("PDF");
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("SUCCEEDED");
        response.setPageCount(maxPageNumber);
        response.setProvider(providerType().wireName());
        response.setParseMode(DocumentParseMode.HIGH_QUALITY.wireName());
        response.setFallbackUsed(false);
        response.setBlocks(blocks);
        response.setElements(elements);
        response.setAssets(assets);
        response.setWarnings(warnings.stream()
                .filter(warning -> warning != null && !warning.isBlank())
                .distinct()
                .toList());
        response.setRawOcrResponse(body);
        response.setElapsedMs(Math.max(0, root.path("elapsedMs").asLong(0)));
        return response;
    }

    private List<TranslationDocumentAssetDto> extractAssets(JsonNode assetsNode) {
        if (!assetsNode.isArray()) {
            return List.of();
        }
        List<TranslationDocumentAssetDto> assets = new ArrayList<>();
        int assetOrder = 1;
        for (JsonNode assetNode : assetsNode) {
            String assetType = normalizeAssetType(firstNonBlank(
                    assetNode.path("assetType").asText(""),
                    assetNode.path("type").asText("")
            ));
            if (!"image".equals(assetType)) {
                continue;
            }
            TranslationDocumentAssetDto asset = new TranslationDocumentAssetDto();
            asset.setId(firstNonBlank(assetNode.path("id").asText(""), "vl-a" + assetOrder));
            asset.setAssetType(assetType);
            asset.setPageNumber(Math.max(1, assetNode.path("pageNumber").asInt(1)));
            asset.setBbox(jsonStringOrNull(assetNode.path("bbox")));
            asset.setRecognizedText(firstNonBlank(
                    assetNode.path("recognizedText").asText(""),
                    assetNode.path("text").asText("")
            ));
            asset.setProvider(assetNode.path("source").asText("paddle_vl"));
            asset.setRecognitionStatus("READY");
            asset.setConfidence(nullableDouble(assetNode, "confidence"));
            Map<String, Object> metadata = extractObjectMetadata(assetNode.path("metadata"));
            metadata.put("mimeType", assetNode.path("mimeType").asText("image/jpeg"));
            metadata.put("rawType", assetNode.path("rawType").asText("image"));
            metadata.put("width", nullableInt(assetNode, "width"));
            metadata.put("height", nullableInt(assetNode, "height"));
            String dataBase64 = assetNode.path("dataBase64").asText("");
            if (!dataBase64.isBlank()) {
                String mimeType = String.valueOf(metadata.getOrDefault("mimeType", "image/jpeg"));
                metadata.put("dataBase64", dataBase64);
                metadata.put("dataUrl", "data:" + mimeType + ";base64," + dataBase64);
            }
            asset.setMetadata(metadata);
            assets.add(asset);
            assetOrder++;
        }
        return assets;
    }

    private String extractPageText(JsonNode pageNode) {
        String explicitText = firstNonBlank(pageNode.path("cleanedText").asText(""), pageNode.path("text").asText(""));
        if (!explicitText.isBlank()) {
            return explicitText;
        }
        List<String> lines = new ArrayList<>();
        JsonNode elementsNode = pageNode.path("elements");
        if (elementsNode.isArray()) {
            for (JsonNode elementNode : elementsNode) {
                String text = elementNode.path("text").asText("");
                if (!text.isBlank()) {
                    lines.add(text);
                }
            }
        }
        JsonNode blocksNode = pageNode.path("blocks");
        if (lines.isEmpty() && blocksNode.isArray()) {
            for (JsonNode blockNode : blocksNode) {
                String text = blockNode.path("text").asText("");
                if (!text.isBlank()) {
                    lines.add(text);
                }
            }
        }
        return String.join("\n", lines).trim();
    }

    private String inferBlockType(String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.startsWith("#") || normalized.length() <= 24 && !normalized.endsWith("。")) {
            return "heading";
        }
        if (normalized.startsWith("|") && normalized.contains("\n|")) {
            return "table";
        }
        return "paragraph";
    }

    private String normalizeElementType(String type) {
        if (type == null || type.isBlank() || "text".equalsIgnoreCase(type)) {
            return "paragraph";
        }
        return type;
    }

    private String normalizeAssetType(String type) {
        if (type == null || type.isBlank()) {
            return "image";
        }
        String normalized = type.trim().toLowerCase();
        if ("figure".equals(normalized) || "picture".equals(normalized) || "diagram".equals(normalized) || "chart".equals(normalized)) {
            return "image";
        }
        return normalized;
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

    private Map<String, Object> extractObjectMetadata(JsonNode node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!node.isObject()) {
            return metadata;
        }
        node.fields().forEachRemaining(entry -> metadata.put(entry.getKey(), jsonValue(entry.getValue())));
        return metadata;
    }

    private Object jsonValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray() || node.isObject()) {
            return objectMapper.convertValue(node, Object.class);
        }
        return node.asText();
    }

    private String jsonStringOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.toString();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }

    private boolean contentTypeContains(String contentType, String expected) {
        return contentType != null && contentType.toLowerCase().contains(expected.toLowerCase());
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://host.docker.internal:8091";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizeEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return "/vl/pdf";
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
