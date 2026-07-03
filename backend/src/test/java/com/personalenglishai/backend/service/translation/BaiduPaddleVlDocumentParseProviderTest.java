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

class BaiduPaddleVlDocumentParseProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void supportsOnlyHighQualityPdfWhenEnabledAndConfigured() {
        BaiduPaddleVlDocumentParseProvider provider = new BaiduPaddleVlDocumentParseProvider(
                true,
                "http://127.0.0.1:65535/layout-parsing",
                "test-token",
                1000,
                20,
                true,
                true,
                true,
                false,
                true,
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
    void sendsPdfToBaiduPaddleVlApiAndMapsMarkdownResult() throws IOException {
        byte[] pdfBytes = "%PDF-baidu-vl".getBytes(StandardCharsets.UTF_8);
        List<JsonNode> capturedRequests = new ArrayList<>();
        List<String> capturedAuthorization = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/layout-parsing", exchange -> {
            capturedAuthorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedRequests.add(objectMapper.readTree(exchange.getRequestBody()));
            String response = """
                    {
                      "errorCode": 0,
                      "errorMsg": "Success",
                      "result": {
                        "layoutParsingResults": [
                          {
                            "markdown": {
                              "text": "# 第一章 概述\\n\\n这是第一段。\\n\\n| A | B |\\n|---|---|\\n| 1 | 2 |"
                            },
                            "prunedResult": {
                              "pageNumber": 1,
                              "blocks": [
                                {"type": "title", "text": "第一章 概述", "bbox": [10, 20, 200, 50]},
                                {"type": "text", "text": "这是第一段。", "bbox": [10, 60, 300, 100]},
                                {"type": "table", "text": "| A | B |\\n|---|---|\\n| 1 | 2 |", "bbox": [10, 120, 400, 220]}
                              ]
                            }
                          }
                        ]
                      }
                    }
                    """;
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        BaiduPaddleVlDocumentParseProvider provider = new BaiduPaddleVlDocumentParseProvider(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/layout-parsing",
                "test-token",
                1500,
                20,
                true,
                true,
                true,
                false,
                true,
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

        assertThat(capturedAuthorization).containsExactly("token test-token");
        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).path("file").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pdfBytes));
        assertThat(capturedRequests.get(0).path("fileType").asInt()).isEqualTo(0);
        assertThat(capturedRequests.get(0).path("useDocOrientationClassify").asBoolean()).isTrue();
        assertThat(capturedRequests.get(0).path("useDocUnwarping").asBoolean()).isTrue();
        assertThat(capturedRequests.get(0).path("useLayoutDetection").asBoolean()).isTrue();
        assertThat(capturedRequests.get(0).path("useChartRecognition").asBoolean()).isFalse();
        assertThat(capturedRequests.get(0).path("prettifyMarkdown").asBoolean()).isTrue();

        assertThat(response.getProvider()).isEqualTo("baidu-paddle-vl");
        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOcrStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getPageCount()).isEqualTo(1);
        assertThat(response.getBlocks()).extracting("text")
                .contains("第一章 概述", "这是第一段。", "| A | B |\n|---|---|\n| 1 | 2 |");
        assertThat(response.getElements()).hasSize(3);
        assertThat(response.getRawOcrResponse()).contains("\"errorCode\": 0");
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
