package com.personalenglishai.backend.service.vocabulary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

class VocabularyGenerationPythonClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INTERNAL_TOKEN = "internal-test-token";

    @Test
    void sends_exact_versioned_contract_with_bearer_token_and_without_prompt_version() throws Exception {
        CapturingExchange exchange = new CapturingExchange(HttpStatus.OK, completeResponse());
        VocabularyGenerationPythonClient client = client(exchange, Duration.ofSeconds(1));

        VocabularyGenerationPythonResponse response = client.generate(request());

        assertEquals("complete", response.outcome());
        ClientRequest captured = exchange.request();
        assertEquals(HttpMethod.POST, captured.method());
        assertEquals("http://python.test/internal/v1/vocabulary/card-generations", captured.url().toString());
        assertEquals(MediaType.APPLICATION_JSON, captured.headers().getContentType());
        assertEquals("Bearer " + INTERNAL_TOKEN, captured.headers().getFirst(HttpHeaders.AUTHORIZATION));

        JsonNode body = OBJECT_MAPPER.readTree(exchange.body());
        assertEquals(1, body.path("contractVersion").asInt());
        assertEquals(1, body.path("coreSchemaVersion").asInt());
        assertEquals("request_123", body.path("requestId").asText());
        assertEquals("trace_123", body.path("traceId").asText());
        assertEquals(45_000, body.path("timeoutBudgetMs").asInt());
        assertEquals("supposed", body.path("term").asText());
        assertEquals("supposed", body.path("dictionaryCore").path("term").asText());
        assertEquals("It is supposed to be easy.", body.path("sourceContext").asText());
        assertEquals("theme_system_exam", body.path("theme").path("uid").asText());
        assertEquals(1, body.path("theme").path("version").asInt());
        assertEquals("Exam", body.path("theme").path("name").asText());
        assertEquals("Exam preparation", body.path("theme").path("purpose").asText());
        assertEquals("exam-markdown-v1", body.path("theme").path("promptStrategyKey").asText());
        assertEquals(1, body.path("theme").path("contentFormatVersion").asInt());
        assertFalse(body.has("promptVersion"));
        assertFalse(body.path("generation").has("promptVersion"));
    }

    @Test
    void parses_complete_and_partial_responses() {
        VocabularyGenerationPythonResponse complete = client(new CapturingExchange(HttpStatus.OK, completeResponse()), Duration.ofSeconds(1))
                .generate(request());
        VocabularyGenerationPythonResponse partial = client(new CapturingExchange(HttpStatus.OK, partialResponse()), Duration.ofSeconds(1))
                .generate(request());

        assertEquals("complete", complete.outcome());
        assertEquals("## Exam focus\n\nUseful collocation.", complete.contentMarkdown());
        assertEquals("openai", complete.generation().provider());
        assertEquals("partial", partial.outcome());
        assertEquals("", partial.contentMarkdown());
        assertEquals("markdown_unavailable", partial.warning());
    }

    @Test
    void rejects_unknown_outcome_and_invalid_response_contract() {
        assertInvalidResponse(completeResponse().replace("\"outcome\":\"complete\"", "\"outcome\":\"other\""));
        assertInvalidResponse(completeResponse().replace("\"term\":\"supposed\"", "\"term\":\"different\""));
        assertInvalidResponse(completeResponse().replace("\"contentMarkdown\":\"## Exam focus\\n\\nUseful collocation.\"", "\"contentMarkdown\":\"\""));
        assertInvalidResponse(completeResponse().replace("\"contractVersion\":1", "\"contractVersion\":2"));
        assertInvalidResponse(completeResponse().replace("\"coreSchemaVersion\":1", "\"coreSchemaVersion\":2"));
        assertInvalidResponse(completeResponse().replace("\"contentFormatVersion\":1", "\"contentFormatVersion\":2"));
        assertInvalidResponse("{not-json}");
    }

    @Test
    void uses_commonmark_html_boundaries_for_response_markdown() {
        String fencedLiteral = completeResponse().replace(
                "## Exam focus\\n\\nUseful collocation.",
                "```html\\n<div>literal</div>\\n```");
        VocabularyGenerationPythonResponse accepted = client(
                new CapturingExchange(HttpStatus.OK, fencedLiteral), Duration.ofSeconds(1))
                .generate(request());

        assertEquals("```html\n<div>literal</div>\n```", accepted.contentMarkdown());
        assertInvalidResponse(completeResponse().replace(
                "## Exam focus\\n\\nUseful collocation.",
                "<!-- hidden -->"));
    }

    @Test
    void rejects_response_trace_id_that_does_not_match_request() {
        assertInvalidResponse(completeResponse().replace("trace_123", "trace_other"));
    }

    @Test
    void requires_http_timeout_to_be_strictly_below_generation_lease() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://python.test")
                .exchangeFunction(new CapturingExchange(HttpStatus.OK, completeResponse()))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new VocabularyGenerationPythonClient(
                        webClient, INTERNAL_TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VocabularyGenerationPythonClient(
                        webClient, INTERNAL_TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(1)));
        new VocabularyGenerationPythonClient(
                webClient, INTERNAL_TOKEN, Duration.ofMillis(999), Duration.ofSeconds(1));
    }

    @Test
    void maps_client_rejections_to_non_retryable_generation_errors_without_body_leakage() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY)) {
            VocabularyGenerationException exception = assertThrows(
                    VocabularyGenerationException.class,
                    () -> client(new CapturingExchange(status, "private response body"), Duration.ofSeconds(1)).generate(request()));

            assertEquals("PYTHON_GENERATION_REQUEST_REJECTED", exception.code());
            assertFalse(exception.retryable());
            assertFalse(exception.getMessage().contains("private response body"));
        }
    }

    @Test
    void maps_authentication_failures_to_non_retryable_infrastructure_errors_without_body_leakage() {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            VocabularyGenerationException exception = assertThrows(
                    VocabularyGenerationException.class,
                    () -> client(new CapturingExchange(status, "private response body"), Duration.ofSeconds(1)).generate(request()));

            assertEquals("PYTHON_GENERATION_AUTH_FAILED", exception.code());
            assertFalse(exception.retryable());
            assertFalse(exception.getMessage().contains("private response body"));
        }
    }

    @Test
    void maps_upstream_failures_to_retryable_generation_errors_without_body_leakage() {
        for (HttpStatus status : List.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)) {
            VocabularyGenerationException exception = assertThrows(
                    VocabularyGenerationException.class,
                    () -> client(new CapturingExchange(status, "private response body"), Duration.ofSeconds(1)).generate(request()));

            assertEquals("PYTHON_GENERATION_UPSTREAM_UNAVAILABLE", exception.code());
            assertTrue(exception.retryable());
            assertFalse(exception.getMessage().contains("private response body"));
        }
    }

    @Test
    void maps_connection_failures_and_client_timeouts_to_retryable_generation_errors() {
        ExchangeFunction connectionFailure = request -> Mono.error(new WebClientRequestException(
                new IOException("connection failed"), HttpMethod.POST, URI.create("http://python.test"), HttpHeaders.EMPTY));
        VocabularyGenerationException connectionException = assertThrows(
                VocabularyGenerationException.class,
                () -> client(connectionFailure, Duration.ofSeconds(1)).generate(request()));
        assertEquals("PYTHON_GENERATION_CONNECTION_FAILED", connectionException.code());
        assertTrue(connectionException.retryable());

        VocabularyGenerationException timeoutException = assertThrows(
                VocabularyGenerationException.class,
                () -> client(request -> Mono.never(), Duration.ofMillis(10)).generate(request()));
        assertEquals("PYTHON_GENERATION_TIMEOUT", timeoutException.code());
        assertTrue(timeoutException.retryable());
    }

    @Test
    void caps_http_timeout_by_the_remaining_request_budget() {
        VocabularyGenerationException timeoutException = assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> assertThrows(
                        VocabularyGenerationException.class,
                        () -> client(request -> Mono.never(), Duration.ofSeconds(2))
                                .generate(request(10))));

        assertEquals("PYTHON_GENERATION_TIMEOUT", timeoutException.code());
        assertTrue(timeoutException.retryable());
    }

    private void assertInvalidResponse(String body) {
        VocabularyGenerationException exception = assertThrows(
                VocabularyGenerationException.class,
                () -> client(new CapturingExchange(HttpStatus.OK, body), Duration.ofSeconds(1)).generate(request()));
        assertEquals("PYTHON_GENERATION_INVALID_RESPONSE", exception.code());
        assertFalse(exception.retryable());
    }

    private VocabularyGenerationPythonClient client(ExchangeFunction exchange, Duration timeout) {
        return new VocabularyGenerationPythonClient(
                WebClient.builder().baseUrl("http://python.test").exchangeFunction(exchange).build(), INTERNAL_TOKEN, timeout);
    }

    private VocabularyGenerationPythonRequest request() {
        return request(45_000);
    }

    private VocabularyGenerationPythonRequest request(int timeoutBudgetMs) {
        return new VocabularyGenerationPythonRequest(
                "request_123",
                "trace_123",
                timeoutBudgetMs,
                "supposed",
                new VocabularyGenerationPythonRequest.Core(
                        "supposed",
                        List.of(new VocabularyGenerationPythonRequest.Phonetic("uk", "səˈpəʊzd", null)),
                        List.of(new VocabularyGenerationPythonRequest.Sense(
                                "adjective",
                                List.of(new VocabularyGenerationPythonRequest.Meaning(
                                        "generally believed or expected", "一般认为的；预期的"))))),
                "It is supposed to be easy.",
                new VocabularyGenerationPythonRequest.Theme(
                        "theme_system_exam", 1, "Exam", "Exam preparation", "exam-markdown-v1", 1));
    }

    private String completeResponse() {
        return """
                {"contractVersion":1,"coreSchemaVersion":1,"core":{"schemaVersion":1,"term":"supposed","phonetics":[{"region":"uk","text":"səˈpəʊzd","audioUrl":null}],"senses":[{"partOfSpeech":"adjective","meanings":[{"definitionEn":"generally believed or expected","definitionZh":"一般认为的；预期的"}]}]},"contentMarkdown":"## Exam focus\\n\\nUseful collocation.","contentFormatVersion":1,"outcome":"complete","warning":null,"generation":{"provider":"openai","model":"test-model","promptVersion":"vocabulary-card-markdown-v1","modelCallCount":1,"traceId":"trace_123"}}
                """;
    }

    private String partialResponse() {
        return completeResponse()
                .replace("\"contentMarkdown\":\"## Exam focus\\n\\nUseful collocation.\"", "\"contentMarkdown\":\"\"")
                .replace("\"outcome\":\"complete\"", "\"outcome\":\"partial\"")
                .replace("\"warning\":null", "\"warning\":\"markdown_unavailable\"");
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
