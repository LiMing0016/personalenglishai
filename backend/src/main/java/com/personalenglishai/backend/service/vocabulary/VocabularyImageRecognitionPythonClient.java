package com.personalenglishai.backend.service.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

@Component
public final class VocabularyImageRecognitionPythonClient {

    private static final String PATH = "/internal/v1/vocabulary/image-recognitions";
    private static final long MAX_TIMEOUT_MS = 55_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String internalToken;
    private final Duration timeout;

    @Autowired
    public VocabularyImageRecognitionPythonClient(
            @Value("${vocabulary.image-recognition.python.base-url:http://127.0.0.1:8011}") String baseUrl,
            @Value("${vocabulary.image-recognition.python.internal-token:}") String internalToken,
            @Value("${vocabulary.image-recognition.python.timeout-ms:55000}") long timeoutMs) {
        this(WebClient.builder().baseUrl(requireBaseUrl(baseUrl)).build(), internalToken, Duration.ofMillis(timeoutMs));
    }

    public VocabularyImageRecognitionPythonClient(WebClient webClient, String internalToken, Duration timeout) {
        if (webClient == null) {
            throw new IllegalArgumentException("webClient is required");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("Python image recognition timeout must be between 1 and 55000ms");
        }
        this.webClient = webClient;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.timeout = timeout;
    }

    public VocabularyImageRecognitionPythonResponse recognize(String traceId, MultipartFile file) {
        if (!validTraceId(traceId) || file == null) {
            throw failure("PYTHON_IMAGE_REQUEST_REJECTED", false, "Python image recognition request was rejected");
        }
        if (internalToken.isBlank()) {
            throw failure("PYTHON_IMAGE_NOT_CONFIGURED", false, "Python image recognition client is not configured");
        }
        try {
            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("contractVersion", "1");
            parts.part("traceId", traceId);
            parts.part("language", "en");
            ByteArrayResource resource = new ByteArrayResource(readBytes(file)) {
                @Override
                public String getFilename() {
                    return safeFileName(file.getOriginalFilename());
                }
            };
            parts.part("file", resource).contentType(contentType(file));
            return exchange(traceId, parts);
        } catch (VocabularyImageRecognitionException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException exception) {
            throw failure("PYTHON_IMAGE_REQUEST_REJECTED", false, "Python image recognition request was rejected");
        } catch (RuntimeException exception) {
            throw transportFailure(exception);
        }
    }

    private VocabularyImageRecognitionPythonResponse exchange(String traceId, MultipartBodyBuilder parts) {
        String body = webClient.post()
                .uri(PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> headers.setBearerAuth(internalToken))
                .bodyValue(parts.build())
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class);
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(errorBody -> Mono.error(statusFailure(response.statusCode(), errorBody)));
                })
                .timeout(timeout)
                .block();
        VocabularyImageRecognitionPythonResponse response = parseResponse(body);
        if (!traceId.equals(response.traceId()) || !traceId.equals(response.generation().traceId())) {
            throw failure("PYTHON_IMAGE_OUTPUT_INVALID", false, "Python image recognition response is invalid");
        }
        return response;
    }

    private VocabularyImageRecognitionPythonResponse parseResponse(String body) {
        try {
            JsonNode node = OBJECT_MAPPER.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body);
            return VocabularyImageRecognitionPythonResponse.fromJson(node);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw failure("PYTHON_IMAGE_OUTPUT_INVALID", false, "Python image recognition response is invalid");
        }
    }

    private VocabularyImageRecognitionException statusFailure(
            org.springframework.http.HttpStatusCode status, String body) {
        int value = status.value();
        if (value == HttpStatus.BAD_REQUEST.value() || value == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            return failure("PYTHON_IMAGE_REQUEST_REJECTED", false, "Python image recognition request was rejected");
        }
        if (value == HttpStatus.UNAUTHORIZED.value() || value == HttpStatus.FORBIDDEN.value()) {
            return failure("PYTHON_IMAGE_AUTH_FAILED", false, "Python image recognition authentication failed");
        }
        if (value == HttpStatus.BAD_GATEWAY.value()) {
            return failure("PYTHON_IMAGE_OUTPUT_INVALID", false, "Python image recognition response is invalid");
        }
        if (value == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            if (hasErrorCode(body, "IMAGE_RECOGNITION_NOT_CONFIGURED")) {
                return failure("PYTHON_IMAGE_NOT_CONFIGURED", false, "Python image recognition service is not configured");
            }
            return failure("PYTHON_IMAGE_UPSTREAM_UNAVAILABLE", true, "Python image recognition service is unavailable");
        }
        if (value == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return failure("PYTHON_IMAGE_TIMEOUT", true, "Python image recognition request timed out");
        }
        return failure("PYTHON_IMAGE_UPSTREAM_UNAVAILABLE", true, "Python image recognition service is unavailable");
    }

    private boolean hasErrorCode(String body, String expectedCode) {
        if (body == null || body.isBlank() || body.length() > 4_096) {
            return false;
        }
        try {
            return expectedCode.equals(OBJECT_MAPPER.readTree(body).path("detail").path("code").asText());
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private VocabularyImageRecognitionException transportFailure(RuntimeException exception) {
        Throwable cause = Exceptions.unwrap(exception);
        if (hasCause(cause, TimeoutException.class)) {
            return failure("PYTHON_IMAGE_TIMEOUT", true, "Python image recognition request timed out");
        }
        if (hasCause(cause, WebClientRequestException.class) || hasCause(cause, ConnectException.class)) {
            return failure("PYTHON_IMAGE_TRANSPORT_FAILED", true, "Python image recognition transport failed");
        }
        return failure("PYTHON_IMAGE_TRANSPORT_FAILED", true, "Python image recognition transport failed");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null && current.getCause() != current; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private byte[] readBytes(MultipartFile file) throws IOException {
        return file.getBytes();
    }

    private MediaType contentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("file content type is required");
        }
        return MediaType.parseMediaType(contentType);
    }

    private static String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "image";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n\\u0000]", "");
        return filename.isBlank() ? "image" : filename;
    }

    private static boolean validTraceId(String traceId) {
        return traceId != null && traceId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Python image recognition base URL is required");
        }
        return baseUrl.trim();
    }

    private VocabularyImageRecognitionException failure(String code, boolean retryable, String message) {
        return new VocabularyImageRecognitionException(code, retryable, message);
    }
}
