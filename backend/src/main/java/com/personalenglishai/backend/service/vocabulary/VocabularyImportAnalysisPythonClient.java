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
public final class VocabularyImportAnalysisPythonClient {

    private static final String PATH = "/internal/v1/vocabulary/import-analyses";
    private static final long MAX_TIMEOUT_MS = 55_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String internalToken;
    private final Duration timeout;

    @Autowired
    public VocabularyImportAnalysisPythonClient(
            @Value("${vocabulary.import-analysis.python.base-url:http://127.0.0.1:8011}") String baseUrl,
            @Value("${vocabulary.import-analysis.python.internal-token:}") String internalToken,
            @Value("${vocabulary.import-analysis.python.timeout-ms:55000}") long timeoutMs) {
        this(WebClient.builder().baseUrl(requireBaseUrl(baseUrl)).build(), internalToken, Duration.ofMillis(timeoutMs));
    }

    public VocabularyImportAnalysisPythonClient(WebClient webClient, String internalToken, Duration timeout) {
        if (webClient == null) {
            throw new IllegalArgumentException("webClient is required");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("Python import analysis timeout must be between 1 and 55000ms");
        }
        this.webClient = webClient;
        this.internalToken = internalToken == null ? "" : internalToken;
        this.timeout = timeout;
    }

    public VocabularyImportAnalysisPythonResponse analyze(
            String traceId,
            String text,
            MultipartFile file,
            String inputFingerprint) {
        if (!validTraceId(traceId) || !validFingerprint(inputFingerprint)
                || ((text == null || text.isBlank()) && (file == null || file.isEmpty()))) {
            throw failure("PYTHON_IMPORT_REQUEST_REJECTED", false, "Python import analysis request was rejected");
        }
        if (internalToken.isBlank()) {
            throw failure("PYTHON_IMPORT_NOT_CONFIGURED", false, "Python import analysis client is not configured");
        }
        try {
            MultipartBodyBuilder parts = new MultipartBodyBuilder();
            parts.part("contractVersion", "1");
            parts.part("traceId", traceId);
            parts.part("inputFingerprint", inputFingerprint);
            parts.part("language", "en");
            parts.part("text", text == null ? "" : text);
            if (file != null && !file.isEmpty()) {
                ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return safeFileName(file.getOriginalFilename());
                    }
                };
                parts.part("file", resource).contentType(contentType(file));
            }
            return exchange(traceId, inputFingerprint, parts);
        } catch (VocabularyImportAnalysisException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException exception) {
            throw failure("PYTHON_IMPORT_REQUEST_REJECTED", false, "Python import analysis request was rejected");
        } catch (RuntimeException exception) {
            throw transportFailure(exception);
        }
    }

    private VocabularyImportAnalysisPythonResponse exchange(
            String traceId,
            String inputFingerprint,
            MultipartBodyBuilder parts) {
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
                            .flatMap(errorBody -> Mono.error(statusFailure(response.statusCode().value(), errorBody)));
                })
                .timeout(timeout)
                .block();
        VocabularyImportAnalysisPythonResponse response = parseResponse(body);
        if (!traceId.equals(response.traceId()) || !traceId.equals(response.generation().traceId())
                || !inputFingerprint.equals(response.inputFingerprint())) {
            throw failure("PYTHON_IMPORT_OUTPUT_INVALID", false, "Python import analysis response is invalid");
        }
        return response;
    }

    private VocabularyImportAnalysisPythonResponse parseResponse(String body) {
        try {
            JsonNode node = OBJECT_MAPPER.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body);
            return VocabularyImportAnalysisPythonResponse.fromJson(node);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw failure("PYTHON_IMPORT_OUTPUT_INVALID", false, "Python import analysis response is invalid");
        }
    }

    private VocabularyImportAnalysisException statusFailure(int status, String body) {
        if (status == 400 || status == 422) {
            return failure("PYTHON_IMPORT_REQUEST_REJECTED", false, "Python import analysis request was rejected");
        }
        if (status == 401 || status == 403) {
            return failure("PYTHON_IMPORT_AUTH_FAILED", false, "Python import analysis authentication failed");
        }
        if (status == 502) {
            return failure("PYTHON_IMPORT_OUTPUT_INVALID", false, "Python import analysis response is invalid");
        }
        if (status == 503 && hasErrorCode(body, "IMPORT_ANALYSIS_NOT_CONFIGURED")) {
            return failure("PYTHON_IMPORT_NOT_CONFIGURED", false, "Python import analysis is not configured");
        }
        if (status == 504) {
            return failure("PYTHON_IMPORT_TIMEOUT", true, "Python import analysis timed out");
        }
        return failure("PYTHON_IMPORT_UPSTREAM_UNAVAILABLE", true, "Python import analysis is unavailable");
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

    private VocabularyImportAnalysisException transportFailure(RuntimeException exception) {
        Throwable cause = Exceptions.unwrap(exception);
        if (hasCause(cause, TimeoutException.class)) {
            return failure("PYTHON_IMPORT_TIMEOUT", true, "Python import analysis timed out");
        }
        if (hasCause(cause, WebClientRequestException.class) || hasCause(cause, ConnectException.class)) {
            return failure("PYTHON_IMPORT_TRANSPORT_FAILED", true, "Python import analysis transport failed");
        }
        return failure("PYTHON_IMPORT_TRANSPORT_FAILED", true, "Python import analysis transport failed");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null && current.getCause() != current; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private MediaType contentType(MultipartFile file) {
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            throw new IllegalArgumentException("file content type is required");
        }
        return MediaType.parseMediaType(file.getContentType());
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

    private static boolean validTraceId(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    }

    private static boolean validFingerprint(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Python import analysis base URL is required");
        }
        return baseUrl.trim();
    }

    private VocabularyImportAnalysisException failure(String code, boolean retryable, String message) {
        return new VocabularyImportAnalysisException(code, retryable, message);
    }
}
