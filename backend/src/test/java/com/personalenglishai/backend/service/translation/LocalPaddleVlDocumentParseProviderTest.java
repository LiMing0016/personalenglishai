package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPaddleVlDocumentParseProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void supportsOnlyHighQualityPdfWhenEnabled() {
        LocalPaddleVlDocumentParseProvider provider = new LocalPaddleVlDocumentParseProvider(
                true,
                "http://127.0.0.1:65535",
                "/vl/pdf",
                "ch,eng",
                1000,
                12,
                260,
                true,
                true,
                false,
                false,
                false,
                objectMapper
        );

        assertThat(provider.supports(request("article.pdf", "application/pdf", DocumentParseMode.HIGH_QUALITY))).isTrue();
        assertThat(provider.supports(request("article.pdf", "application/pdf", DocumentParseMode.STANDARD))).isFalse();
        assertThat(provider.supports(new DocumentParseRequest(
                "article.txt",
                "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8),
                "TXT",
                DocumentParseMode.HIGH_QUALITY,
                "immersive"
        ))).isFalse();
    }

    @Test
    void sendsPdfToLocalPaddleVlServiceAndMapsOcrResponse() throws IOException {
        byte[] pdfBytes = "%PDF-local-vl".getBytes(StandardCharsets.UTF_8);
        List<JsonNode> capturedRequests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vl/pdf", exchange -> {
            capturedRequests.add(objectMapper.readTree(exchange.getRequestBody()));
            String response = """
                    {
                      "status": "SUCCEEDED",
                      "provider": "PaddleOCR-VL",
                      "pageCount": 1,
                      "recognizedPageCount": 1,
                      "elapsedMs": 88,
                      "warnings": [],
                      "metadata": {"engine": "PaddleOCR-VL"},
                      "assets": [
                        {
                          "id": "p1-vl-a1",
                          "assetType": "image",
                          "pageNumber": 1,
                          "bbox": [[30,120],[330,120],[330,260],[30,260]],
                          "mimeType": "image/jpeg",
                          "dataBase64": "ZmFrZS1pbWFnZQ==",
                          "width": 300,
                          "height": 140,
                          "order": 1,
                          "source": "paddle_vl",
                          "rawType": "image",
                          "confidence": 0.87
                        }
                      ],
                      "pages": [
                        {
                          "pageNumber": 1,
                          "text": "# 第一章 概述\\n\\n这是第一段。",
                          "cleanedText": "# 第一章 概述\\n\\n这是第一段。",
                          "rawText": "第一章 概述\\n这是第一段。",
                          "width": 1200,
                          "height": 1600,
                          "confidence": 0.92,
                          "elements": [
                            {"type": "heading", "text": "第一章 概述", "bbox": [[10,20],[200,20],[200,50],[10,50]], "confidence": 0.98, "order": 1, "source": "paddle_vl", "rawType": "title"},
                            {"type": "paragraph", "text": "这是第一段。", "bbox": [[10,60],[300,60],[300,100],[10,100]], "confidence": 0.91, "order": 2, "source": "paddle_vl", "rawType": "text"}
                          ]
                        }
                      ]
                    }
                    """;
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        LocalPaddleVlDocumentParseProvider provider = new LocalPaddleVlDocumentParseProvider(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/vl/pdf",
                "ch,eng",
                1500,
                12,
                260,
                true,
                true,
                false,
                false,
                false,
                objectMapper
        );

        TranslationDocumentParseResponse response = provider.parse(new DocumentParseRequest(
                "article.pdf",
                "application/pdf",
                pdfBytes,
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive"
        ));

        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).path("documentBase64").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pdfBytes));
        assertThat(capturedRequests.get(0).path("language").asText()).isEqualTo("ch,eng");
        assertThat(capturedRequests.get(0).path("parseMode").asText()).isEqualTo("high_quality");
        assertThat(capturedRequests.get(0).path("maxPages").asInt()).isEqualTo(12);
        assertThat(capturedRequests.get(0).path("dpi").asInt()).isEqualTo(260);
        assertThat(capturedRequests.get(0).path("enableLayout").asBoolean()).isTrue();
        assertThat(capturedRequests.get(0).path("enableTable").asBoolean()).isTrue();
        assertThat(capturedRequests.get(0).path("enableFormula").asBoolean()).isFalse();
        assertThat(capturedRequests.get(0).path("enableOrientation").asBoolean()).isFalse();
        assertThat(capturedRequests.get(0).path("enableUnwarping").asBoolean()).isFalse();

        assertThat(response.getProvider()).isEqualTo("local-paddle-vl");
        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOcrStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getPageCount()).isEqualTo(1);
        assertThat(response.getBlocks()).extracting("text").contains("# 第一章 概述\n\n这是第一段。");
        assertThat(response.getElements()).hasSize(2);
        assertThat(response.getElements()).extracting("provider").containsOnly("paddle_vl");
        assertThat(response.getAssets()).hasSize(1);
        assertThat(response.getAssets().get(0).getAssetType()).isEqualTo("image");
        assertThat(response.getAssets().get(0).getBbox()).contains("[[30,120]");
        assertThat(response.getAssets().get(0).getMetadata())
                .containsEntry("mimeType", "image/jpeg")
                .containsEntry("dataBase64", "ZmFrZS1pbWFnZQ==")
                .containsEntry("dataUrl", "data:image/jpeg;base64,ZmFrZS1pbWFnZQ==");
        assertThat(response.getRawOcrResponse()).contains("\"PaddleOCR-VL\"");
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("本地 PaddleOCR-VL"));
    }

    @Test
    void sendsRequestedPageWindowToLocalPaddleVlService() throws IOException {
        byte[] pdfBytes = "%PDF-local-vl-page-window".getBytes(StandardCharsets.UTF_8);
        List<JsonNode> capturedRequests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/vl/pdf", exchange -> {
            capturedRequests.add(objectMapper.readTree(exchange.getRequestBody()));
            String response = """
                    {
                      "status": "SUCCEEDED",
                      "provider": "PaddleOCR-VL",
                      "pageCount": 2,
                      "recognizedPageCount": 2,
                      "elapsedMs": 88,
                      "warnings": [],
                      "metadata": {"engine": "PaddleOCR-VL"},
                      "pages": [
                        {
                          "pageNumber": 11,
                          "text": "第十一页正文",
                          "cleanedText": "第十一页正文",
                          "elements": [
                            {"type": "paragraph", "text": "第十一页正文", "bbox": [[10,60],[300,60],[300,100],[10,100]], "confidence": 0.91, "order": 1, "source": "paddle_vl", "rawType": "text"}
                          ]
                        }
                      ]
                    }
                    """;
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        LocalPaddleVlDocumentParseProvider provider = new LocalPaddleVlDocumentParseProvider(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/vl/pdf",
                "ch,eng",
                1500,
                500,
                260,
                true,
                true,
                false,
                false,
                false,
                objectMapper
        );

        TranslationDocumentParseResponse response = provider.parse(new DocumentParseRequest(
                "book.pdf",
                "application/pdf",
                pdfBytes,
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive",
                DocumentParseProviderPreference.LOCAL_PADDLE_VL,
                11,
                20,
                10
        ));

        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).path("pageStart").asInt()).isEqualTo(11);
        assertThat(capturedRequests.get(0).path("pageEnd").asInt()).isEqualTo(20);
        assertThat(capturedRequests.get(0).path("maxPages").asInt()).isEqualTo(10);
        assertThat(response.getBlocks()).extracting("pageNumber").containsExactly(11);
        assertThat(response.getElements()).extracting("pageNumber").containsExactly(11);
    }

    private DocumentParseRequest request(String filename, String contentType, DocumentParseMode parseMode) {
        return new DocumentParseRequest(
                filename,
                contentType,
                "%PDF-test".getBytes(StandardCharsets.UTF_8),
                "PDF",
                parseMode,
                "immersive"
        );
    }
}
