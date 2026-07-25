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
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

class VocabularyGenerationPythonClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INTERNAL_TOKEN = "internal-test-token";

    @Test
    void sendsExactVersionedContractWithBearerToken() throws Exception {
        CapturingExchange exchange = new CapturingExchange(HttpStatus.OK, completeResponse());
        VocabularyGenerationPythonResponse response = client(exchange, Duration.ofSeconds(1)).generate(request());

        assertEquals("complete", response.outcome());
        ClientRequest captured = exchange.request();
        assertEquals(HttpMethod.POST, captured.method());
        assertEquals("Bearer " + INTERNAL_TOKEN, captured.headers().getFirst(HttpHeaders.AUTHORIZATION));
        JsonNode body = OBJECT_MAPPER.readTree(exchange.body());
        assertEquals(2, body.path("contractVersion").asInt());
        assertEquals(2, body.path("coreSchemaVersion").asInt());
        assertEquals(1, body.path("cardBlocksSchemaVersion").asInt());
        assertEquals("sense_1", body.path("dictionaryCore").path("senses").get(0).path("id").asText());
        assertEquals("exam-blocks-v1", body.path("theme").path("promptStrategyKey").asText());
        assertFalse(body.has("promptVersion"));
    }

    @Test
    void parsesCompleteAndPartialResponses() {
        VocabularyGenerationPythonResponse complete = client(
                new CapturingExchange(HttpStatus.OK, completeResponse()), Duration.ofSeconds(1)).generate(request());
        VocabularyGenerationPythonResponse partial = client(
                new CapturingExchange(HttpStatus.OK, partialResponse()), Duration.ofSeconds(1)).generate(request());

        assertEquals("exampleList", complete.cardBlocks().path("blocks").get(0).path("type").asText());
        assertEquals("openai", complete.generation().provider());
        assertEquals("partial", partial.outcome());
        assertTrue(partial.cardBlocks().path("blocks").isEmpty());
        assertEquals("card_blocks_unavailable", partial.warning());
    }

    @Test
    void rejectsInvalidResponseContractsAndLegacyMarkdownFields() {
        assertInvalidResponse(completeResponse().replace("\"outcome\":\"complete\"", "\"outcome\":\"other\""));
        assertInvalidResponse(completeResponse().replace("\"contractVersion\":2", "\"contractVersion\":1"));
        assertInvalidResponse(completeResponse().replace("\"coreSchemaVersion\":2", "\"coreSchemaVersion\":1"));
        assertInvalidResponse(completeResponse().replace("\"cardBlocksSchemaVersion\":1", "\"cardBlocksSchemaVersion\":2"));
        assertInvalidResponse(completeResponse().replace(
                "\"outcome\":\"complete\"",
                "\"contentMarkdown\":\"legacy\",\"outcome\":\"complete\""));
        assertInvalidResponse("{not-json}");
    }

    @Test
    void rejectsResponseTraceIdThatDoesNotMatchRequest() {
        assertInvalidResponse(completeResponse().replace("trace_123", "trace_other"));
    }

    @Test
    void requiresHttpTimeoutBelowGenerationLease() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://python.test")
                .exchangeFunction(new CapturingExchange(HttpStatus.OK, completeResponse()))
                .build();
        assertThrows(IllegalArgumentException.class, () -> new VocabularyGenerationPythonClient(
                webClient, INTERNAL_TOKEN, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        new VocabularyGenerationPythonClient(
                webClient, INTERNAL_TOKEN, Duration.ofMillis(999), Duration.ofSeconds(1));
    }

    @Test
    void mapsHttpAndConnectionFailuresWithoutBodyLeakage() {
        VocabularyGenerationException rejected = assertThrows(
                VocabularyGenerationException.class,
                () -> client(new CapturingExchange(HttpStatus.BAD_REQUEST, "private body"), Duration.ofSeconds(1))
                        .generate(request()));
        assertEquals("PYTHON_GENERATION_REQUEST_REJECTED", rejected.code());
        assertFalse(rejected.retryable());
        assertFalse(rejected.getMessage().contains("private body"));

        VocabularyGenerationException upstream = assertThrows(
                VocabularyGenerationException.class,
                () -> client(new CapturingExchange(HttpStatus.SERVICE_UNAVAILABLE, "private body"), Duration.ofSeconds(1))
                        .generate(request()));
        assertEquals("PYTHON_GENERATION_UPSTREAM_UNAVAILABLE", upstream.code());
        assertTrue(upstream.retryable());

        ExchangeFunction connectionFailure = ignored -> Mono.error(new WebClientRequestException(
                new IOException("connection failed"), HttpMethod.POST,
                URI.create("http://python.test"), HttpHeaders.EMPTY));
        VocabularyGenerationException connection = assertThrows(
                VocabularyGenerationException.class,
                () -> client(connectionFailure, Duration.ofSeconds(1)).generate(request()));
        assertEquals("PYTHON_GENERATION_CONNECTION_FAILED", connection.code());
        assertTrue(connection.retryable());
    }

    @Test
    void capsHttpTimeoutByRemainingRequestBudget() {
        VocabularyGenerationException timeout = assertTimeoutPreemptively(
                Duration.ofMillis(500),
                () -> assertThrows(
                        VocabularyGenerationException.class,
                        () -> client(ignored -> Mono.never(), Duration.ofSeconds(2)).generate(request(10))));
        assertEquals("PYTHON_GENERATION_TIMEOUT", timeout.code());
        assertTrue(timeout.retryable());
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
                WebClient.builder().baseUrl("http://python.test").exchangeFunction(exchange).build(),
                INTERNAL_TOKEN,
                timeout);
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
                                "sense_1",
                                "adjective",
                                List.of(new VocabularyGenerationPythonRequest.Meaning(
                                        "meaning_1_1",
                                        "generally believed or expected",
                                        "一般认为的；预期的"))))),
                "It is supposed to be easy.",
                new VocabularyGenerationPythonRequest.Theme(
                        "theme_system_exam", 1, "Exam", "Exam preparation", "exam-blocks-v1", 1));
    }

    private String completeResponse() {
        return """
                {"contractVersion":2,"coreSchemaVersion":2,"cardBlocksSchemaVersion":1,"core":{"schemaVersion":2,"term":"supposed","phonetics":[{"region":"uk","text":"səˈpəʊzd","audioUrl":null}],"senses":[{"id":"sense_1","partOfSpeech":"adjective","meanings":[{"id":"meaning_1_1","definitionEn":"generally believed or expected","definitionZh":"一般认为的；预期的"}]}]},"cardBlocks":{"schemaVersion":1,"blocks":[{"id":"block_examples_01","type":"exampleList","title":"常用例句","meaningRefs":["meaning_1_1"],"format":"structured","content":{"items":[{"sentence":"It is supposed to be easy.","translation":"这应该很容易。"}]},"source":"ai","sourceRef":null,"sortOrder":10,"userEdited":false,"locked":false}]},"outcome":"complete","warning":null,"generation":{"provider":"openai","model":"test-model","promptVersion":"vocabulary-card-blocks-v1","modelCallCount":2,"traceId":"trace_123"}}
                """;
    }

    private String partialResponse() {
        return """
                {"contractVersion":2,"coreSchemaVersion":2,"cardBlocksSchemaVersion":1,"core":{"schemaVersion":2,"term":"supposed","phonetics":[{"region":"uk","text":"səˈpəʊzd","audioUrl":null}],"senses":[{"id":"sense_1","partOfSpeech":"adjective","meanings":[{"id":"meaning_1_1","definitionEn":"generally believed or expected","definitionZh":"一般认为的；预期的"}]}]},"cardBlocks":{"schemaVersion":1,"blocks":[]},"outcome":"partial","warning":"card_blocks_unavailable","generation":{"provider":"openai","model":"test-model","promptVersion":"vocabulary-card-blocks-v1","modelCallCount":1,"traceId":"trace_123"}}
                """;
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
