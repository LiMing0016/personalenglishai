package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;

@Component
public final class VocabularyGenerationPythonClient {

    private static final String PATH = "/internal/v1/vocabulary/card-generations";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String internalToken;
    private final Duration timeout;

    @Autowired
    public VocabularyGenerationPythonClient(
            @Value("${vocabulary.generation.python.base-url:http://127.0.0.1:8011}") String baseUrl,
            @Value("${vocabulary.generation.python.internal-token:}") String internalToken,
            @Value("${vocabulary.generation.python.timeout-ms:60000}") long timeoutMs) {
        this(WebClient.builder().baseUrl(requireBaseUrl(baseUrl)).build(), internalToken, Duration.ofMillis(timeoutMs));
    }

    public VocabularyGenerationPythonClient(WebClient webClient, String internalToken, Duration timeout) {
        if (webClient == null) {
            throw new IllegalArgumentException("webClient is required");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()
                || timeout.toMillis() > VocabularyGenerationPythonRequest.MAX_TIMEOUT_BUDGET_MS) {
            throw new IllegalArgumentException("Python generation timeout must be between 1 and 60000ms");
        }
        this.webClient = webClient;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.timeout = timeout;
    }

    public VocabularyGenerationPythonResponse generate(VocabularyGenerationPythonRequest request) {
        if (request == null) {
            throw failure("PYTHON_GENERATION_INVALID_REQUEST", false, "Python generation request is invalid");
        }
        if (internalToken.isBlank()) {
            throw failure("PYTHON_GENERATION_NOT_CONFIGURED", false, "Python generation client is not configured");
        }
        try {
            String body = webClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(internalToken))
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(String.class);
                        }
                        return response.releaseBody().then(reactor.core.publisher.Mono.error(statusFailure(response.statusCode())));
                    })
                    .timeout(timeout)
                    .block();
            VocabularyGenerationPythonResponse response = parseResponse(body);
            if (!request.term().equals(response.core().term())) {
                throw failure("PYTHON_GENERATION_INVALID_RESPONSE", false, "Python generation response is invalid");
            }
            return response;
        } catch (VocabularyGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw transportFailure(exception);
        }
    }

    private VocabularyGenerationPythonResponse parseResponse(String body) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(body);
            return VocabularyGenerationPythonResponse.fromJson(node);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw failure("PYTHON_GENERATION_INVALID_RESPONSE", false, "Python generation response is invalid");
        }
    }

    private VocabularyGenerationException statusFailure(org.springframework.http.HttpStatusCode status) {
        int value = status.value();
        if (value == HttpStatus.BAD_REQUEST.value() || value == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            return failure("PYTHON_GENERATION_REQUEST_REJECTED", false, "Python generation request was rejected");
        }
        if (value == HttpStatus.UNAUTHORIZED.value() || value == HttpStatus.FORBIDDEN.value()) {
            return failure("PYTHON_GENERATION_AUTH_FAILED", false, "Python generation authentication failed");
        }
        if (value == HttpStatus.INTERNAL_SERVER_ERROR.value()
                || value == HttpStatus.SERVICE_UNAVAILABLE.value()
                || value == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return failure("PYTHON_GENERATION_UPSTREAM_UNAVAILABLE", true, "Python generation service is unavailable");
        }
        return failure("PYTHON_GENERATION_UPSTREAM_FAILURE", false, "Python generation service returned an unexpected status");
    }

    private VocabularyGenerationException transportFailure(RuntimeException exception) {
        Throwable cause = Exceptions.unwrap(exception);
        if (hasCause(cause, TimeoutException.class)) {
            return failure("PYTHON_GENERATION_TIMEOUT", true, "Python generation request timed out");
        }
        if (hasCause(cause, WebClientRequestException.class) || hasCause(cause, ConnectException.class)) {
            return failure("PYTHON_GENERATION_CONNECTION_FAILED", true, "Python generation connection failed");
        }
        return failure("PYTHON_GENERATION_TRANSPORT_FAILED", true, "Python generation transport failed");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null && current.getCause() != current; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Python generation base URL is required");
        }
        return baseUrl.trim();
    }

    private VocabularyGenerationException failure(String code, boolean retryable, String message) {
        return new VocabularyGenerationException(code, retryable, message);
    }
}
