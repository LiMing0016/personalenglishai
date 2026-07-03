package com.personalenglishai.backend.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaddleTranslationOcrServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsPdfToLocalPaddleServerAndMapsPageText() throws IOException {
        byte[] pdfBytes = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        List<JsonNode> capturedRequests = new java.util.ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr/pdf", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            capturedRequests.add(request);
            String response = """
                    {
                      "pages": [
                        {"pageNumber": 1, "text": "Paddle OCR recovered page one."},
                        {"pageNumber": 2, "text": "第二页中文和 English mixed text."}
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

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        PaddleTranslationOcrService service = new PaddleTranslationOcrService(
                true,
                baseUrl,
                "/ocr/pdf",
                "ch,eng",
                1500,
                objectMapper
        );

        TranslationOcrResult result = service.recognizePdf(pdfBytes);

        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.getPages())
                .extracting(TranslationOcrPageText::getPageNumber)
                .containsExactly(1, 2);
        assertThat(result.getPages())
                .extracting(TranslationOcrPageText::getText)
                .containsExactly("Paddle OCR recovered page one.", "第二页中文和 English mixed text.");
        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).get("documentBase64").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(pdfBytes));
        assertThat(capturedRequests.get(0).get("language").asText()).isEqualTo("ch,eng");
        assertThat(capturedRequests.get(0).path("parseMode").asText()).isEqualTo("standard");
        assertThat(capturedRequests.get(0).path("maxPages").asInt()).isEqualTo(20);
        assertThat(capturedRequests.get(0).path("dpi").asInt()).isEqualTo(220);
        assertThat(capturedRequests.get(0).path("enableLayout").asBoolean()).isFalse();
        assertThat(capturedRequests.get(0).path("enableTable").asBoolean()).isFalse();
        assertThat(capturedRequests.get(0).path("enableFormula").asBoolean()).isFalse();
        assertThat(capturedRequests.get(0).path("enableOrientation").asBoolean()).isTrue();
        assertThat(capturedRequests.get(0).path("enableUnwarping").asBoolean()).isFalse();
    }

    @Test
    void standardRequestDisablesHighQualityFeaturesEvenWhenDefaultConfigIsHighQuality() throws IOException {
        byte[] pdfBytes = "%PDF-standard".getBytes(StandardCharsets.UTF_8);
        List<JsonNode> capturedRequests = new java.util.ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr/pdf", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            capturedRequests.add(request);
            String response = """
                    {
                      "pages": [
                        {"pageNumber": 1, "text": "standard mode text"}
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

        PaddleTranslationOcrService service = new PaddleTranslationOcrService(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/ocr/pdf",
                "ch,eng",
                1500,
                "high_quality",
                20,
                220,
                true,
                true,
                true,
                true,
                true,
                objectMapper
        );

        TranslationOcrResult result = service.recognizePdf(pdfBytes, DocumentParseMode.STANDARD);

        assertThat(result.isSucceeded()).isTrue();
        assertThat(capturedRequests).hasSize(1);
        JsonNode request = capturedRequests.get(0);
        assertThat(request.path("parseMode").asText()).isEqualTo("standard");
        assertThat(request.path("enableLayout").asBoolean()).isFalse();
        assertThat(request.path("enableTable").asBoolean()).isFalse();
        assertThat(request.path("enableFormula").asBoolean()).isFalse();
        assertThat(request.path("enableOrientation").asBoolean()).isTrue();
        assertThat(request.path("enableUnwarping").asBoolean()).isFalse();
    }

    @Test
    void trimsValidPdfToConfiguredMaxPagesBeforeSendingToPaddle() throws IOException {
        byte[] pdfBytes = createPdfWithPages(4);
        List<JsonNode> capturedRequests = new java.util.ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr/pdf", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            capturedRequests.add(request);
            String response = """
                    {
                      "pages": [
                        {"pageNumber": 1, "text": "first page"},
                        {"pageNumber": 2, "text": "second page"}
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

        PaddleTranslationOcrService service = new PaddleTranslationOcrService(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/ocr/pdf",
                "ch,eng",
                1500,
                "standard",
                2,
                220,
                false,
                false,
                false,
                true,
                false,
                objectMapper
        );

        TranslationOcrResult result = service.recognizePdf(pdfBytes);

        assertThat(result.isSucceeded()).isTrue();
        assertThat(capturedRequests).hasSize(1);
        byte[] sentPdfBytes = Base64.getDecoder().decode(capturedRequests.get(0).get("documentBase64").asText());
        try (PDDocument sentDocument = PDDocument.load(sentPdfBytes)) {
            assertThat(sentDocument.getNumberOfPages()).isEqualTo(2);
        }
        assertThat(capturedRequests.get(0).path("maxPages").asInt()).isEqualTo(2);
    }

    @Test
    void sendsRequestedPdfPageRangeAndRestoresOriginalPageNumbers() throws IOException {
        byte[] pdfBytes = createPdfWithPages(12);
        List<JsonNode> capturedRequests = new java.util.ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr/pdf", exchange -> {
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            capturedRequests.add(request);
            String response = """
                    {
                      "pages": [
                        {"pageNumber": 1, "text": "page eleven text"},
                        {"pageNumber": 2, "text": "page twelve text"}
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

        PaddleTranslationOcrService service = new PaddleTranslationOcrService(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/ocr/pdf",
                "ch,eng",
                1500,
                "standard",
                20,
                220,
                false,
                false,
                false,
                true,
                false,
                objectMapper
        );

        TranslationOcrResult result = service.recognizePdf(
                pdfBytes,
                new TranslationOcrOptions(DocumentParseMode.STANDARD, 11, 12, 10)
        );

        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.getPages())
                .extracting(TranslationOcrPageText::getPageNumber)
                .containsExactly(11, 12);
        assertThat(capturedRequests).hasSize(1);
        byte[] sentPdfBytes = Base64.getDecoder().decode(capturedRequests.get(0).get("documentBase64").asText());
        try (PDDocument sentDocument = PDDocument.load(sentPdfBytes)) {
            assertThat(sentDocument.getNumberOfPages()).isEqualTo(2);
        }
        assertThat(capturedRequests.get(0).path("maxPages").asInt()).isEqualTo(10);
    }

    @Test
    void returnsUnavailableWhenPaddleProviderIsDisabled() {
        PaddleTranslationOcrService service = new PaddleTranslationOcrService(
                false,
                "http://127.0.0.1:65535",
                "/ocr/pdf",
                "ch,eng",
                100,
                objectMapper
        );

        TranslationOcrResult result = service.recognizePdf("pdf".getBytes(StandardCharsets.UTF_8));

        assertThat(result.getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(result.getMessage()).contains("PaddleOCR");
    }

    @Test
    void mapsStructuredBlocksAndFormulaPlaceholdersWhenTextIsMissing() throws IOException {
        byte[] pdfBytes = "%PDF-structured".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr/pdf", exchange -> {
            String response = """
                    {
                      "status": "PARTIAL",
                      "pages": [
                        {
                          "pageNumber": 3,
                          "text": "First OCR block\\nSecond OCR block",
                          "blocks": [
                            {"text": "First OCR block", "confidence": 0.98, "order": 1},
                            {"text": "Second OCR block", "confidence": 0.94, "order": 2}
                          ],
                          "elements": [
                            {
                              "type": "paragraph",
                              "text": "First OCR block",
                              "bbox": [[1,2],[3,2],[3,4],[1,4]],
                              "confidence": 0.98,
                              "order": 1,
                              "source": "paddle_ocr",
                              "rawType": "text",
                              "warnings": ["LOW_CONFIDENCE_TEXT"]
                            }
                          ],
                          "formulas": [
                            {"latex": "E = mc^2", "confidence": 0.88}
                          ],
                          "warnings": ["LOW_CONFIDENCE"]
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

        PaddleTranslationOcrService service = new PaddleTranslationOcrService(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/ocr/pdf",
                "ch,eng",
                1500,
                objectMapper
        );

        TranslationOcrResult result = service.recognizePdf(pdfBytes);

        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.getPages()).hasSize(1);
        assertThat(result.getPages().get(0).getPageNumber()).isEqualTo(3);
        assertThat(result.getPages().get(0).getText())
                .isEqualTo("First OCR block\nSecond OCR block\n[FORMULA: E = mc^2]");
        assertThat(result.getPages().get(0).getElements()).hasSize(2);
        assertThat(result.getPages().get(0).getElements().get(0).getText()).isEqualTo("First OCR block");
        assertThat(result.getPages().get(0).getElements().get(0).getBbox()).contains("[[1,2]");
        assertThat(result.getPages().get(0).getElements().get(0).getSource()).isEqualTo("paddle_ocr");
        assertThat(result.getRawResponse()).contains("\"status\": \"PARTIAL\"");
    }

    private byte[] createPdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
