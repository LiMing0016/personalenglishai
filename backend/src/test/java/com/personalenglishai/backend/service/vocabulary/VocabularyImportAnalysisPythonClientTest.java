package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class VocabularyImportAnalysisPythonClientTest {

    private static final String INTERNAL_TOKEN = "internal-test-token";
    private static final String TRACE_ID = "trace_123";
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void sends_exact_text_only_multipart_contract_and_preserves_fingerprint() {
        CapturingExchange exchange = new CapturingExchange(HttpStatus.OK, response());
        VocabularyImportAnalysisPythonClient client = client(exchange, Duration.ofSeconds(55));

        VocabularyImportAnalysisPythonResponse result = client.analyze(
                TRACE_ID, "package", null, FINGERPRINT);

        assertEquals(FINGERPRINT, result.inputFingerprint());
        ClientRequest captured = exchange.request();
        assertEquals(HttpMethod.POST, captured.method());
        assertEquals("http://python.test/internal/v1/vocabulary/import-analyses", captured.url().toString());
        assertEquals("Bearer " + INTERNAL_TOKEN, captured.headers().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(
                List.of("contractVersion", "traceId", "inputFingerprint", "language", "text"),
                multipartPartNames(exchange.body()));
        assertTrue(exchange.body().contains(FINGERPRINT));
        assertTrue(exchange.body().contains("package"));
    }

    @Test
    void appends_image_part_for_combined_input() {
        CapturingExchange exchange = new CapturingExchange(HttpStatus.OK, response());

        client(exchange, Duration.ofSeconds(55)).analyze(
                TRACE_ID,
                "package",
                image("../words.png"),
                FINGERPRINT);

        assertEquals(
                List.of("contractVersion", "traceId", "inputFingerprint", "language", "text", "file"),
                multipartPartNames(exchange.body()));
        assertTrue(exchange.body().contains("filename=\"words.png\""));
        assertFalse(exchange.body().contains("../"));
    }

    @Test
    void rejects_response_fingerprint_or_trace_mismatch() {
        assertOutputInvalid(response().replace(FINGERPRINT, "b".repeat(64)));
        assertOutputInvalid(response().replace(
                "\"traceId\":\"trace_123\",\"inputFingerprint\"",
                "\"traceId\":\"trace_other\",\"inputFingerprint\""));
    }

    @Test
    void enforces_55_second_timeout_ceiling_and_maps_client_timeout() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://python.test")
                .exchangeFunction(new CapturingExchange(HttpStatus.OK, response()))
                .build();
        new VocabularyImportAnalysisPythonClient(webClient, INTERNAL_TOKEN, Duration.ofSeconds(55));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VocabularyImportAnalysisPythonClient(
                        webClient, INTERNAL_TOKEN, Duration.ofMillis(55_001)));

        VocabularyImportAnalysisException timeout = assertThrows(
                VocabularyImportAnalysisException.class,
                () -> client(request -> Mono.never(), Duration.ofMillis(10))
                        .analyze(TRACE_ID, "package", null, FINGERPRINT));
        assertEquals("PYTHON_IMPORT_TIMEOUT", timeout.code());
        assertTrue(timeout.retryable());
    }

    private void assertOutputInvalid(String body) {
        VocabularyImportAnalysisException exception = assertThrows(
                VocabularyImportAnalysisException.class,
                () -> client(new CapturingExchange(HttpStatus.OK, body), Duration.ofSeconds(55))
                        .analyze(TRACE_ID, "package", null, FINGERPRINT));
        assertEquals("PYTHON_IMPORT_OUTPUT_INVALID", exception.code());
        assertFalse(exception.retryable());
    }

    private VocabularyImportAnalysisPythonClient client(ExchangeFunction exchange, Duration timeout) {
        return new VocabularyImportAnalysisPythonClient(
                WebClient.builder().baseUrl("http://python.test").exchangeFunction(exchange).build(),
                INTERNAL_TOKEN,
                timeout);
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile(
                "file",
                filename,
                MediaType.IMAGE_PNG_VALUE,
                "image-data".getBytes(StandardCharsets.UTF_8));
    }

    private String response() {
        return """
                {"contractVersion":1,"traceId":"trace_123","inputFingerprint":"%s","rawText":"package","warnings":[],"items":[{"itemId":"item_1","observedText":"package","normalizedTerm":"package","status":"accepted","suggestions":[],"contextText":null,"confidence":0.98,"evidence":"text"}],"generation":{"provider":"openai","model":"test-model","promptVersion":"vocabulary-import-analysis-v1","modelCallCount":1,"traceId":"trace_123","usage":{"inputTokens":11,"outputTokens":7}}}
                """.formatted(FINGERPRINT);
    }

    private List<String> multipartPartNames(String body) {
        Matcher matcher = Pattern.compile("(?m)^Content-Disposition: form-data; name=\"([^\"]+)\"")
                .matcher(body);
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static final class CapturingExchange implements ExchangeFunction {
        private final HttpStatus status;
        private final String responseBody;
        private final AtomicReference<ClientRequest> request = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();

        private CapturingExchange(HttpStatus status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public Mono<ClientResponse> exchange(ClientRequest clientRequest) {
            request.set(clientRequest);
            MockClientHttpRequest output = new MockClientHttpRequest(clientRequest.method(), clientRequest.url());
            output.setWriteHandler(buffers -> DataBufferUtils.join(buffers)
                    .doOnNext(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        body.set(new String(bytes, StandardCharsets.UTF_8));
                    })
                    .then());
            return clientRequest.body().insert(output, new TestBodyInserterContext())
                    .thenReturn(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(responseBody)
                            .build());
        }

        private ClientRequest request() {
            return request.get();
        }

        private String body() {
            return body.get();
        }
    }

    private static final class TestBodyInserterContext implements BodyInserter.Context {
        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return ExchangeStrategies.withDefaults().messageWriters();
        }

        @Override
        public Optional<ServerHttpRequest> serverRequest() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> hints() {
            return Map.of();
        }
    }
}
