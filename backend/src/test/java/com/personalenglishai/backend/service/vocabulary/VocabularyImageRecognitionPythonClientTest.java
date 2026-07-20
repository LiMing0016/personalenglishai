package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
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
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

class VocabularyImageRecognitionPythonClientTest {

    private static final String INTERNAL_TOKEN = "internal-test-token";
    private static final String TRACE_ID = "trace_123";

    @Test
    void sends_exact_multipart_contract_with_bearer_token_and_sanitized_filename() {
        CapturingExchange exchange = new CapturingExchange(HttpStatus.OK, response());
        VocabularyImageRecognitionPythonClient client = client(exchange, Duration.ofSeconds(55));

        VocabularyImageRecognitionPythonResponse response = client.recognize(TRACE_ID, image("../vocabulary.png"));

        assertEquals(1, response.contractVersion());
        ClientRequest captured = exchange.request();
        assertEquals(HttpMethod.POST, captured.method());
        assertEquals("http://python.test/internal/v1/vocabulary/image-recognitions", captured.url().toString());
        assertEquals("Bearer " + INTERNAL_TOKEN, captured.headers().getFirst(HttpHeaders.AUTHORIZATION));
        assertTrue(captured.headers().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA));
        assertEquals(List.of("contractVersion", "traceId", "language", "file"), multipartPartNames(exchange.body()));
        assertTrue(exchange.body().contains("\r\n1\r\n"));
        assertTrue(exchange.body().contains(TRACE_ID));
        assertTrue(exchange.body().contains("\r\nen\r\n"));
        assertTrue(exchange.body().contains("name=\"file\"; filename=\"vocabulary.png\""));
        assertFalse(exchange.body().contains("../"));
        assertTrue(exchange.body().contains("image-data"));
    }

    @Test
    void parses_usage_and_known_python_warning() {
        VocabularyImageRecognitionPythonResponse response = client(
                new CapturingExchange(HttpStatus.OK, response()), Duration.ofSeconds(55))
                .recognize(TRACE_ID, image("vocabulary.png"));

        assertEquals("visible source text", response.rawText());
        assertEquals(List.of("CANDIDATE_LIMIT_REACHED"), response.warnings());
        assertEquals("accepted", response.items().get(0).status());
        assertEquals(0.98d, response.items().get(0).confidence());
        assertEquals(Integer.valueOf(11), response.generation().usage().inputTokens());
        assertEquals(Integer.valueOf(7), response.generation().usage().outputTokens());
    }

    @Test
    void rejects_unknown_fields_invalid_item_state_unknown_warning_and_trace_mismatches() {
        assertOutputInvalid(response().replace("\"generation\":{", "\"unexpected\":true,\"generation\":{"));
        assertOutputInvalid(response().replace("\"status\":\"accepted\"", "\"status\":\"unknown\""));
        assertOutputInvalid(response().replace("\"suggestions\":[]", "\"suggestions\":[\"receive\"]"));
        assertOutputInvalid(response().replace("CANDIDATE_LIMIT_REACHED", "DICTIONARY_VERIFICATION_UNAVAILABLE"));
        assertOutputInvalid(response().replace("\"modelCallCount\":1,\"traceId\":\"trace_123\"",
                "\"modelCallCount\":1,\"traceId\":\"trace_other\""));
        assertOutputInvalid(response().replace("\"traceId\":\"trace_123\",\"rawText\"", "\"traceId\":\"trace_other\",\"rawText\""));
    }

    @Test
    void rejects_trailing_json_tokens_after_an_otherwise_valid_response() {
        assertOutputInvalid(response() + "\n{}");
    }

    @Test
    void rejects_timeout_configuration_above_the_55_second_image_budget() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://python.test")
                .exchangeFunction(new CapturingExchange(HttpStatus.OK, response()))
                .build();

        new VocabularyImageRecognitionPythonClient(webClient, INTERNAL_TOKEN, Duration.ofSeconds(55));
        assertThrows(IllegalArgumentException.class,
                () -> new VocabularyImageRecognitionPythonClient(webClient, INTERNAL_TOKEN, Duration.ofMillis(55_001)));
    }

    @Test
    void rejects_missing_internal_token_without_making_a_request() {
        VocabularyImageRecognitionException exception = assertThrows(
                VocabularyImageRecognitionException.class,
                () -> client(new CapturingExchange(HttpStatus.OK, response()), Duration.ofSeconds(55), "")
                        .recognize(TRACE_ID, image("vocabulary.png")));

        assertEquals("PYTHON_IMAGE_NOT_CONFIGURED", exception.code());
        assertFalse(exception.retryable());
    }

    @Test
    void maps_http_failures_without_leaking_response_bodies() {
        assertFailure(HttpStatus.BAD_REQUEST, "private body", "PYTHON_IMAGE_REQUEST_REJECTED", false);
        assertFailure(HttpStatus.UNPROCESSABLE_ENTITY, "private body", "PYTHON_IMAGE_REQUEST_REJECTED", false);
        assertFailure(HttpStatus.UNAUTHORIZED, "private body", "PYTHON_IMAGE_AUTH_FAILED", false);
        assertFailure(HttpStatus.FORBIDDEN, "private body", "PYTHON_IMAGE_AUTH_FAILED", false);
        assertFailure(HttpStatus.BAD_GATEWAY, "private body", "PYTHON_IMAGE_OUTPUT_INVALID", false);
        assertFailure(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"detail\":{\"code\":\"IMAGE_RECOGNITION_NOT_CONFIGURED\",\"message\":\"private body\"}}",
                "PYTHON_IMAGE_NOT_CONFIGURED", false);
        assertFailure(HttpStatus.SERVICE_UNAVAILABLE, "private body", "PYTHON_IMAGE_UPSTREAM_UNAVAILABLE", true);
        assertFailure(HttpStatus.GATEWAY_TIMEOUT, "private body", "PYTHON_IMAGE_TIMEOUT", true);
    }

    @Test
    void maps_client_timeout_and_transport_failure() {
        VocabularyImageRecognitionException timeout = assertThrows(
                VocabularyImageRecognitionException.class,
                () -> client(request -> Mono.never(), Duration.ofMillis(10)).recognize(TRACE_ID, image("vocabulary.png")));
        assertEquals("PYTHON_IMAGE_TIMEOUT", timeout.code());
        assertTrue(timeout.retryable());

        ExchangeFunction transportFailure = request -> Mono.error(new WebClientRequestException(
                new IOException("private transport failure"), HttpMethod.POST,
                URI.create("http://python.test"), HttpHeaders.EMPTY));
        VocabularyImageRecognitionException transport = assertThrows(
                VocabularyImageRecognitionException.class,
                () -> client(transportFailure, Duration.ofSeconds(55)).recognize(TRACE_ID, image("vocabulary.png")));
        assertEquals("PYTHON_IMAGE_TRANSPORT_FAILED", transport.code());
        assertTrue(transport.retryable());
        assertFalse(transport.getMessage().contains("private transport failure"));
    }

    private void assertOutputInvalid(String body) {
        VocabularyImageRecognitionException exception = assertThrows(
                VocabularyImageRecognitionException.class,
                () -> client(new CapturingExchange(HttpStatus.OK, body), Duration.ofSeconds(55))
                        .recognize(TRACE_ID, image("vocabulary.png")));
        assertEquals("PYTHON_IMAGE_OUTPUT_INVALID", exception.code());
        assertFalse(exception.retryable());
    }

    private void assertFailure(HttpStatus status, String body, String code, boolean retryable) {
        VocabularyImageRecognitionException exception = assertThrows(
                VocabularyImageRecognitionException.class,
                () -> client(new CapturingExchange(status, body), Duration.ofSeconds(55))
                        .recognize(TRACE_ID, image("vocabulary.png")));
        assertEquals(code, exception.code());
        assertEquals(retryable, exception.retryable());
        assertFalse(exception.getMessage().contains("private body"));
    }

    private VocabularyImageRecognitionPythonClient client(ExchangeFunction exchange, Duration timeout) {
        return client(exchange, timeout, INTERNAL_TOKEN);
    }

    private VocabularyImageRecognitionPythonClient client(ExchangeFunction exchange, Duration timeout, String token) {
        return new VocabularyImageRecognitionPythonClient(
                WebClient.builder().baseUrl("http://python.test").exchangeFunction(exchange).build(), token, timeout);
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE,
                "image-data".getBytes(StandardCharsets.UTF_8));
    }

    private String response() {
        return """
                {"contractVersion":1,"traceId":"trace_123","rawText":"visible source text","warnings":["CANDIDATE_LIMIT_REACHED"],"items":[{"itemId":"item_1","observedText":"receive","normalizedTerm":"receive","status":"accepted","suggestions":[],"contextText":null,"confidence":0.98}],"generation":{"provider":"openai","model":"test-model","promptVersion":"vocabulary-image-recognition-v1","modelCallCount":1,"traceId":"trace_123","usage":{"inputTokens":11,"outputTokens":7}}}
                """;
    }

    private List<String> multipartPartNames(String body) {
        Matcher matcher = Pattern.compile("(?m)^Content-Disposition: form-data; name=\"([^\"]+)\"").matcher(body);
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
