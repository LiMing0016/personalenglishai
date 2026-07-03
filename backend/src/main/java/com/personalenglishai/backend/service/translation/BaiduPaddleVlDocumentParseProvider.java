package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
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
@ConditionalOnProperty(name = "app.document-parse.baidu-paddle-vl.enabled", havingValue = "true")
public class BaiduPaddleVlDocumentParseProvider implements DocumentParseProvider {
    private final boolean enabled;
    private final String apiUrl;
    private final String token;
    private final Duration timeout;
    private final int maxPages;
    private final boolean useDocOrientationClassify;
    private final boolean useDocUnwarping;
    private final boolean useLayoutDetection;
    private final boolean useChartRecognition;
    private final boolean prettifyMarkdown;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public BaiduPaddleVlDocumentParseProvider(
            @Value("${app.document-parse.baidu-paddle-vl.enabled:false}") boolean enabled,
            @Value("${app.document-parse.baidu-paddle-vl.api-url:}") String apiUrl,
            @Value("${app.document-parse.baidu-paddle-vl.token:}") String token,
            @Value("${app.document-parse.baidu-paddle-vl.timeout-ms:300000}") long timeoutMs,
            @Value("${app.document-parse.baidu-paddle-vl.max-pages:20}") int maxPages,
            @Value("${app.document-parse.baidu-paddle-vl.use-doc-orientation-classify:true}") boolean useDocOrientationClassify,
            @Value("${app.document-parse.baidu-paddle-vl.use-doc-unwarping:true}") boolean useDocUnwarping,
            @Value("${app.document-parse.baidu-paddle-vl.use-layout-detection:true}") boolean useLayoutDetection,
            @Value("${app.document-parse.baidu-paddle-vl.use-chart-recognition:false}") boolean useChartRecognition,
            @Value("${app.document-parse.baidu-paddle-vl.prettify-markdown:true}") boolean prettifyMarkdown,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
        this.token = token == null ? "" : token.trim();
        this.timeout = Duration.ofMillis(Math.max(100, timeoutMs));
        this.maxPages = Math.max(1, Math.min(1000, maxPages));
        this.useDocOrientationClassify = useDocOrientationClassify;
        this.useDocUnwarping = useDocUnwarping;
        this.useLayoutDetection = useLayoutDetection;
        this.useChartRecognition = useChartRecognition;
        this.prettifyMarkdown = prettifyMarkdown;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return enabled
                && !apiUrl.isBlank()
                && !token.isBlank()
                && request != null
                && request.parseMode() == DocumentParseMode.HIGH_QUALITY
                && supportsPdf(request);
    }

    @Override
    public DocumentParseProviderType providerType() {
        return DocumentParseProviderType.BAIDU_PADDLE_VL;
    }

    @Override
    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        if (!supports(request)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "百度 PaddleOCR-VL Provider 未启用或配置不完整");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(request));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "token " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "百度 PaddleOCR-VL API 返回异常状态: " + httpResponse.statusCode());
            }
            return parseResponse(request, httpResponse.body());
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "百度 PaddleOCR-VL API 不可用: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "百度 PaddleOCR-VL API 调用被中断");
        } catch (RuntimeException e) {
            if (e instanceof BizException) {
                throw e;
            }
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "百度 PaddleOCR-VL API 响应解析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRequest(DocumentParseRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("file", Base64.getEncoder().encodeToString(request.bytes()));
        body.put("fileType", 0);
        body.put("fileName", request.originalFilename());
        body.put("maxPages", maxPages);
        body.put("useDocOrientationClassify", useDocOrientationClassify);
        body.put("useDocUnwarping", useDocUnwarping);
        body.put("useLayoutDetection", useLayoutDetection);
        body.put("useChartRecognition", useChartRecognition);
        body.put("prettifyMarkdown", prettifyMarkdown);
        return body;
    }

    private TranslationDocumentParseResponse parseResponse(DocumentParseRequest request, String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        int errorCode = root.path("errorCode").asInt(0);
        if (errorCode != 0) {
            String errorMessage = root.path("errorMsg").asText("百度 PaddleOCR-VL API 解析失败");
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, errorMessage);
        }

        JsonNode layoutResults = root.path("result").path("layoutParsingResults");
        if (!layoutResults.isArray() || layoutResults.isEmpty()) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "百度 PaddleOCR-VL API 未返回解析结果");
        }

        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId(UUID.randomUUID().toString());
        response.setFileName(request.originalFilename());
        response.setSourceType("PDF");
        response.setProvider(providerType().wireName());
        response.setParseMode(DocumentParseMode.HIGH_QUALITY.wireName());
        response.setParseStatus("SUCCEEDED");
        response.setOcrStatus("SUCCEEDED");
        response.setPageCount(layoutResults.size());
        response.setRawOcrResponse(body);
        List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
        List<TranslationDocumentElementDto> elements = new ArrayList<>();
        int order = 1;
        int elementOrder = 1;
        for (int index = 0; index < layoutResults.size(); index++) {
            JsonNode pageNode = layoutResults.get(index);
            int pageNumber = pageNode.path("prunedResult").path("pageNumber").asInt(index + 1);
            Extraction extraction = extractPage(pageNode, pageNumber, order, elementOrder);
            blocks.addAll(extraction.blocks());
            elements.addAll(extraction.elements());
            order += extraction.blocks().size();
            elementOrder += extraction.elements().size();
        }
        response.setBlocks(blocks);
        response.setElements(elements);
        response.setWarnings(List.of("已使用百度 PaddleOCR-VL API 生成高质量解析结果。"));
        return response;
    }

    private Extraction extractPage(JsonNode pageNode, int pageNumber, int blockStartOrder, int elementStartOrder) {
        List<TranslationDocumentBlockDto> blocks = new ArrayList<>();
        List<TranslationDocumentElementDto> elements = new ArrayList<>();
        JsonNode rawBlocks = pageNode.path("prunedResult").path("blocks");
        int order = blockStartOrder;
        int elementOrder = elementStartOrder;
        if (rawBlocks.isArray() && !rawBlocks.isEmpty()) {
            for (JsonNode rawBlock : rawBlocks) {
                String text = rawBlock.path("text").asText("").strip();
                if (text.isBlank()) {
                    continue;
                }
                String type = normalizeBlockType(rawBlock.path("type").asText(""));
                blocks.add(new TranslationDocumentBlockDto(
                        "p" + pageNumber + "-baidu-vl-b" + order,
                        type,
                        order,
                        pageNumber,
                        text,
                        nullableDouble(rawBlock, "confidence")
                ));
                elements.add(buildElement(rawBlock, pageNumber, elementOrder, type, text));
                order++;
                elementOrder++;
            }
        }
        if (blocks.isEmpty()) {
            for (String text : splitMarkdownBlocks(pageNode.path("markdown").path("text").asText(""))) {
                String type = normalizeMarkdownBlockType(text);
                blocks.add(new TranslationDocumentBlockDto(
                        "p" + pageNumber + "-baidu-vl-b" + order,
                        type,
                        order,
                        pageNumber,
                        cleanMarkdownBlockText(text),
                        null
                ));
                elements.add(buildMarkdownElement(pageNumber, elementOrder, type, cleanMarkdownBlockText(text)));
                order++;
                elementOrder++;
            }
        }
        return new Extraction(blocks, elements);
    }

    private TranslationDocumentElementDto buildElement(JsonNode rawBlock, int pageNumber, int order, String type, String text) {
        TranslationDocumentElementDto element = new TranslationDocumentElementDto();
        element.setId("p" + pageNumber + "-baidu-vl-e" + order);
        element.setType(type);
        element.setOrder(order);
        element.setPageNumber(pageNumber);
        element.setText(text);
        element.setBbox(normalizeBbox(rawBlock.path("bbox")));
        element.setProvider(providerType().wireName());
        element.setConfidence(nullableDouble(rawBlock, "confidence"));
        element.setRecognitionStatus("READY");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rawType", rawBlock.path("type").asText(""));
        element.setMetadata(metadata);
        return element;
    }

    private TranslationDocumentElementDto buildMarkdownElement(int pageNumber, int order, String type, String text) {
        TranslationDocumentElementDto element = new TranslationDocumentElementDto();
        element.setId("p" + pageNumber + "-baidu-vl-e" + order);
        element.setType(type);
        element.setOrder(order);
        element.setPageNumber(pageNumber);
        element.setText(text);
        element.setProvider(providerType().wireName());
        element.setRecognitionStatus("READY");
        return element;
    }

    private List<String> splitMarkdownBlocks(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        for (String block : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\\n\\s*\\n")) {
            String normalized = block.strip();
            if (!normalized.isBlank()) {
                blocks.add(normalized);
            }
        }
        return blocks;
    }

    private String normalizeMarkdownBlockType(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.startsWith("#")) {
            return "heading";
        }
        if (trimmed.startsWith("|")) {
            return "table";
        }
        return "paragraph";
    }

    private String cleanMarkdownBlockText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceFirst("^#{1,6}\\s+", "").strip();
    }

    private String normalizeBlockType(String rawType) {
        String normalized = rawType == null ? "" : rawType.toLowerCase();
        if (normalized.contains("title") || normalized.contains("heading")) {
            return "heading";
        }
        if (normalized.contains("table")) {
            return "table";
        }
        if (normalized.contains("formula") || normalized.contains("equation")) {
            return "formula";
        }
        if (normalized.contains("image") || normalized.contains("figure") || normalized.contains("chart")) {
            return "image";
        }
        return "paragraph";
    }

    private String normalizeBbox(JsonNode bboxNode) {
        if (bboxNode == null || bboxNode.isMissingNode() || bboxNode.isNull()) {
            return null;
        }
        if (bboxNode.isArray() && bboxNode.size() == 4 && bboxNode.get(0).isNumber()) {
            double x1 = bboxNode.get(0).asDouble();
            double y1 = bboxNode.get(1).asDouble();
            double x2 = bboxNode.get(2).asDouble();
            double y2 = bboxNode.get(3).asDouble();
            return "[[" + trimDouble(x1) + "," + trimDouble(y1) + "],[" + trimDouble(x2) + "," + trimDouble(y1)
                    + "],[" + trimDouble(x2) + "," + trimDouble(y2) + "],[" + trimDouble(x1) + "," + trimDouble(y2) + "]]";
        }
        return bboxNode.toString();
    }

    private String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private Double nullableDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private boolean supportsPdf(DocumentParseRequest request) {
        return "PDF".equalsIgnoreCase(request.fileType())
                || TranslationDocumentFileTypes.hasExtension(request.originalFilename(), "pdf")
                || contentTypeContains(request.contentType(), "pdf");
    }

    private boolean contentTypeContains(String contentType, String expected) {
        return contentType != null && contentType.toLowerCase().contains(expected.toLowerCase());
    }

    private record Extraction(List<TranslationDocumentBlockDto> blocks, List<TranslationDocumentElementDto> elements) {
    }
}
