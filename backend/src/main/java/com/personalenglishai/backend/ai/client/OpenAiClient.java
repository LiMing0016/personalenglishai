package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;
import java.net.UnknownHostException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;

/**
 * NOTE: cleaned corrupted comment (encoding issue).
 * NOTE: cleaned corrupted comment (encoding issue).
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String ENDPOINT_MODE_CHAT_COMPLETIONS = "chat_completions";
    private static final String ENDPOINT_MODE_RESPONSES = "responses";
    private static final String PROMPT_VERSION = "v1";
    private static final Random RANDOM = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;
    private final String apiKey;
    private final OpenAiClientConfig config;
    private final CircuitBreaker circuitBreaker;
    /** Proxy resolved from Spring config only (openai.client.*). */
    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;
    /** Whether current profile is dev/local for debug-fail injection. */
    private final boolean isDevOrLocal;
    private final String baseUrl;
    private final String activeProfile;
    private final String model;
    private final String endpointMode;
    private final String fallbackModel;
    private final boolean aiPromptDebugEnabled;
    private final boolean promptRawLogEnabled;
    private final int promptRawLogMaxChars;
    private final long effectiveOverallTimeoutMs;

    // Per-call overrides (thread-local would be safer but this is simpler for single-threaded use)
    private volatile Double overrideTemperature;
    private volatile Integer overrideMaxTokens;

    public OpenAiClient(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${spring.profiles.active:}") String activeProfile,
            @Value("${AI_MODEL:" + DEFAULT_MODEL + "}") String model,
            @Value("${AI_ENDPOINT_MODE:" + ENDPOINT_MODE_CHAT_COMPLETIONS + "}") String endpointMode,
            @Value("${AI_FALLBACK_MODEL:" + DEFAULT_MODEL + "}") String fallbackModel,
            @Value("${AI_PROMPT_DEBUG:false}") boolean aiPromptDebugEnabled,
            @Value("${ai.prompt.log-raw-enabled:false}") boolean promptRawLogEnabled,
            @Value("${ai.prompt.log-raw-max-chars:12000}") int promptRawLogMaxChars,
            OpenAiClientConfig config) {
        this.apiKey = apiKey;
        this.config = config;
        this.activeProfile = activeProfile == null ? "" : activeProfile.toLowerCase();
        this.isDevOrLocal = isDevOrLocalProfile(activeProfile);
        this.model = isBlank(model) ? DEFAULT_MODEL : model.trim();
        this.endpointMode = normalizeEndpointMode(endpointMode);
        this.fallbackModel = isBlank(fallbackModel) ? DEFAULT_MODEL : fallbackModel.trim();
        this.aiPromptDebugEnabled = aiPromptDebugEnabled;
        this.promptRawLogEnabled = promptRawLogEnabled;
        this.promptRawLogMaxChars = Math.max(2000, promptRawLogMaxChars);
        this.effectiveOverallTimeoutMs = resolveEffectiveOverallTimeoutMs(config);
        this.circuitBreaker = new CircuitBreaker(
                config.getCircuitBreakerFailureThreshold(),
                config.getCircuitBreakerWindowMs(),
                config.getCircuitBreakerRecoveryMs()
        );

        String baseUrl = config.getBaseUrl() != null && !config.getBaseUrl().isBlank()
                ? config.getBaseUrl()
                : "https://api.openai.com";
        this.baseUrl = baseUrl;

        // NOTE: cleaned corrupted comment (encoding issue).
        boolean useProxy = false;
        String host = null;
        int port = 80;

        // NOTE: cleaned corrupted comment (encoding issue).
        if (config.isProxyEnabled()) {
            // NOTE: cleaned corrupted comment (encoding issue).
            if (config.getProxyUrl() != null && !config.getProxyUrl().isBlank()) {
                try {
                    URI u = URI.create(config.getProxyUrl().trim());
                    if (u.getHost() != null) {
                        host = u.getHost();
                        port = u.getPort() > 0 ? u.getPort() : 80;
                        useProxy = true;
                    }
                } catch (Exception ignored) {
                    // NOTE: cleaned corrupted comment (encoding issue).
                }
            }
            // NOTE: cleaned corrupted comment (encoding issue).
            if (!useProxy && config.getProxyHost() != null && !config.getProxyHost().isBlank() && config.getProxyPort() != null) {
                host = config.getProxyHost();
                port = config.getProxyPort();
                useProxy = true;
            }
        }
        // NOTE: Proxy must come from Spring configuration only.

        this.proxyEnabled = useProxy;
        this.proxyHost = host;
        this.proxyPort = port;

        // NOTE: cleaned corrupted comment (encoding issue).
        HttpClient httpClient = HttpClient.create();
        if (proxyEnabled) {
            final String proxyHostFinal = proxyHost;
            final int proxyPortFinal = proxyPort;
            httpClient = httpClient.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHostFinal)
                    .port(proxyPortFinal)
            );
        }
        httpClient = httpClient
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(config.getResponseTimeoutMs()));

        // NOTE: cleaned corrupted comment (encoding issue).
        log.info("OpenAI proxy enabled={} proxyHost={} proxyPort={}",
                proxyEnabled,
                proxyHost != null ? proxyHost : "",
                proxyEnabled ? proxyPort : "");
        if (this.activeProfile.contains("prod") && !proxyEnabled && this.baseUrl.contains("api.openai.com")) {
            log.warn("OpenAI direct egress mode in prod (no proxy). If outbound 443 is restricted, configure OPENAI_PROXY_URL.");
        }
        log.info("OpenAI runtime config endpointMode={} model={} fallbackModel={} aiPromptDebugEnabled={}",
                this.endpointMode, this.model, this.fallbackModel, this.aiPromptDebugEnabled);
        log.info("OpenAI timeout config responseTimeoutMs={} configuredOverallTimeoutMs={} effectiveOverallTimeoutMs={} maxRetries={} maxBackoffMs={}",
                config.getResponseTimeoutMs(),
                config.getOverallTimeoutMs(),
                this.effectiveOverallTimeoutMs,
                config.getMaxRetries(),
                config.getMaxBackoffMs());

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null && !apiKey.isEmpty() ? apiKey : ""))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
    public String getModel() { return model; }

    public String call(String systemPrompt, String userPrompt) {
        return callWithTraceId(systemPrompt, userPrompt, null, null);
    }

    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId) {
        return callWithTraceId(systemPrompt, userPrompt, traceId, null);
    }

    /**
     * Call with custom temperature and maxTokens.
     */
    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId,
                                   Double temperature, Integer maxTokens) {
        this.overrideTemperature = temperature;
        this.overrideMaxTokens = maxTokens;
        try {
            return callWithTraceId(systemPrompt, userPrompt, traceId, null);
        } finally {
            this.overrideTemperature = null;
            this.overrideMaxTokens = null;
        }
    }

    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId, String xDebugFail) {
        long startTime = System.currentTimeMillis();
        int inputLength = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        AtomicInteger attemptCounter = new AtomicInteger(1);
        int draftChars = extractDraftChars(userPrompt);
        boolean fallbackUsed = false;
        boolean parseSuccess = false;
        int payloadBytes = 0;
        String effectiveEndpoint = endpointMode;
        String effectiveModel = model;
        String output;

        try {
            if (isDevOrLocal && xDebugFail != null && !xDebugFail.isBlank()) {
                String code = xDebugFail.trim();
                if ("429".equals(code)) {
                    throw new TooManyRequests("Debug injected 429");
                }
                if ("502".equals(code)) {
                    throw WebClientResponseException.create(502, "Bad Gateway", HttpHeaders.EMPTY, null, StandardCharsets.UTF_8);
                }
            }

            Retry retrySpec = Retry.backoff(config.getMaxRetries(), Duration.ofMillis(config.getInitialBackoffMs()))
                    .maxBackoff(Duration.ofMillis(config.getMaxBackoffMs()))
                    .filter(this::shouldRetry)
                    .doBeforeRetry(retrySignal -> attemptCounter.incrementAndGet());

            OpenAiCallResult callResult;
            try {
                callResult = callByMode(effectiveEndpoint, effectiveModel, systemPrompt, userPrompt, traceId, retrySpec);
            } catch (Exception e) {
                if (shouldFallbackFromResponses(effectiveEndpoint, e)) {
                    fallbackUsed = true;
                    effectiveEndpoint = ENDPOINT_MODE_CHAT_COMPLETIONS;
                    effectiveModel = fallbackModel;
                    log.warn("OpenAI fallback engaged traceId={} fromEndpoint={} fromModel={} toEndpoint={} toModel={} reason={} errorCode={} httpStatus={}",
                            traceId,
                            endpointMode,
                            model,
                            effectiveEndpoint,
                            effectiveModel,
                            safeMsg(e),
                            extractOpenAiErrorCode(e),
                            extractHttpStatus(e));
                    callResult = callByMode(effectiveEndpoint, effectiveModel, systemPrompt, userPrompt, traceId, retrySpec);
                } else {
                    throw e;
                }
            }

            output = callResult.content();
            payloadBytes = callResult.payloadBytes();
            parseSuccess = callResult.parseSuccess();

            circuitBreaker.recordSuccess();
            long latency = System.currentTimeMillis() - startTime;
            log.info("OpenAI call succeeded traceId={} attempt={} latencyMs={} httpStatus=200 inputLength={} outputLength={}",
                    traceId, attemptCounter.get(), latency, inputLength, output.length());
            log.info("OpenAI call metrics traceId={} prompt_version={} endpoint={} model={} payload_bytes={} input_chars={} draft_chars={} response_ms={} parse_success={} fallback_used={}",
                    traceId, PROMPT_VERSION, effectiveEndpoint, effectiveModel, payloadBytes, inputLength, draftChars, latency, parseSuccess, fallbackUsed);
            return output;

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            Throwable root = rootCause(e);
            String errorType = classifyError(e);
            String httpStatus = extractHttpStatus(e);
            String rootCauseClass = root != null ? root.getClass().getSimpleName() : "";
            String rootCauseMsg = root != null ? safeMsg(root) : "";
            String openaiRequestId = extractOpenAiRequestId(e);
            String responseBody = sanitizeError(extractResponseBody(e));

            circuitBreaker.recordFailure();
            log.error("OpenAI call failed traceId={} attempt={} latencyMs={} errorType={} httpStatus={} rootCauseClass={} rootCauseMsg={} inputLength={} fallbackUsed={} endpoint={} model={}{} responseBody={}",
                    traceId, attemptCounter.get(), latency, errorType, httpStatus != null ? httpStatus : "",
                    rootCauseClass, rootCauseMsg, inputLength, fallbackUsed, effectiveEndpoint, effectiveModel,
                    openaiRequestId != null ? " openaiRequestId=" + openaiRequestId : "",
                    responseBody);

            String errorMsg = "Failed to call OpenAI API";
            if (e instanceof TooManyRequests) {
                errorMsg = "AI service rate limited, please try again";
            } else if (e instanceof WebClientResponseException we) {
                int status = we.getStatusCode().value();
                if (status == 429) {
                    errorMsg = "AI service rate limited, please try again";
                } else if (status >= 500) {
                    errorMsg = "OpenAI API upstream error";
                } else {
                    errorMsg = "OpenAI API error: " + status;
                }
            } else if (e instanceof WebClientRequestException) {
                errorMsg = "Request timeout or network error";
            }

            throw new RuntimeException(errorMsg, e);
        }
    }

    private static boolean isDevOrLocalProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return false;
        }
        String p = profile.toLowerCase();
        return p.contains("dev") || p.contains("local");
    }

    private OpenAiCallResult callByMode(String endpointMode,
                                        String model,
                                        String systemPrompt,
                                        String userPrompt,
                                        String traceId,
                                        Retry retrySpec) {
        if (ENDPOINT_MODE_RESPONSES.equals(endpointMode)) {
            return callResponses(model, systemPrompt, userPrompt, traceId, retrySpec);
        }
        return callChatCompletions(model, systemPrompt, userPrompt, traceId, retrySpec);
    }

    private OpenAiCallResult callChatCompletions(String model,
                                                 String systemPrompt,
                                                 String userPrompt,
                                                 String traceId,
                                                 Retry retrySpec) {
        ChatRequest request = new ChatRequest(model, List.of(
                new Message("system", systemPrompt == null ? "" : systemPrompt),
                new Message("user", userPrompt == null ? "" : userPrompt)
        ));
        if (overrideTemperature != null) request.setTemperature(overrideTemperature);
        if (overrideMaxTokens != null) request.setMaxTokens(overrideMaxTokens);
        int inputChars = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        int draftChars = extractDraftChars(userPrompt);
        int payloadBytes = logFinalPayload(traceId, ENDPOINT_MODE_CHAT_COMPLETIONS, model, request, inputChars, draftChars);
        logPromptPayload(traceId, request, ENDPOINT_MODE_CHAT_COMPLETIONS);

        ChatResponse response = webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                .retryWhen(retrySpec)
                .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                .block();

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty() ||
                response.getChoices().get(0).getMessage() == null) {
            throw new RuntimeException("Empty response from OpenAI API");
        }
        String content = response.getChoices().get(0).getMessage().getContent();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Empty content in OpenAI response");
        }
        return new OpenAiCallResult(content, payloadBytes, true);
    }

    private OpenAiCallResult callResponses(String model,
                                           String systemPrompt,
                                           String userPrompt,
                                           String traceId,
                                           Retry retrySpec) {
        ResponsesRequest request = new ResponsesRequest(model, List.of(
                new ResponseInputItem("system", List.of(new ResponseContentItem("input_text", systemPrompt == null ? "" : systemPrompt))),
                new ResponseInputItem("user", List.of(new ResponseContentItem("input_text", userPrompt == null ? "" : userPrompt)))
        ));
        int inputChars = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        int draftChars = extractDraftChars(userPrompt);
        int payloadBytes = logFinalPayload(traceId, ENDPOINT_MODE_RESPONSES, model, request, inputChars, draftChars);

        JsonNode responseNode = webClient.post()
                .uri("/v1/responses")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                .retryWhen(retrySpec)
                .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                .block();

        String content = extractResponsesText(responseNode);
        if (isBlank(content)) {
            throw new RuntimeException("Empty content in OpenAI responses API response");
        }
        return new OpenAiCallResult(content, payloadBytes, true);
    }

    private String extractResponsesText(JsonNode responseNode) {
        if (responseNode == null) {
            return "";
        }
        JsonNode outputText = responseNode.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText("");
        }
        if (outputText.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : outputText) {
                if (n.isTextual()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(n.asText(""));
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }

        JsonNode output = responseNode.path("output");
        if (output.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode contentNodes = item.path("content");
                if (!contentNodes.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : contentNodes) {
                    String type = contentItem.path("type").asText("");
                    if ("output_text".equals(type) || "text".equals(type)) {
                        String text = contentItem.path("text").asText("");
                        if (!isBlank(text)) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(text);
                        }
                    }
                }
            }
            return sb.toString();
        }

        return "";
    }

    private int logFinalPayload(String traceId,
                                String endpoint,
                                String model,
                                Object requestPayload,
                                int inputChars,
                                int draftChars) {
        String json = "";
        try {
            json = objectMapper.writeValueAsString(requestPayload);
        } catch (Exception e) {
            log.warn("OpenAI payload serialization failed traceId={} endpoint={} model={} reason={}",
                    traceId, endpoint, model, safeMsg(e));
        }
        int payloadBytes = json.getBytes(StandardCharsets.UTF_8).length;

        if (aiPromptDebugEnabled && !activeProfile.contains("prod")) {
            String body = json;
            boolean truncated = false;
            if (body.length() > promptRawLogMaxChars) {
                body = body.substring(0, promptRawLogMaxChars);
                truncated = true;
            }
            log.info("FINAL OPENAI PAYLOAD ({}) traceId={} model={} payloadBytes={} inputChars={} draftChars={} body={}{}",
                    endpoint,
                    traceId,
                    model,
                    payloadBytes,
                    inputChars,
                    draftChars,
                    redactForLog(body),
                    truncated ? " [truncated]" : "");
        }
        return payloadBytes;
    }

    private boolean shouldFallbackFromResponses(String endpointMode, Throwable t) {
        if (!ENDPOINT_MODE_RESPONSES.equals(endpointMode)) {
            return false;
        }
        String status = extractHttpStatus(t);
        if (status == null || !status.startsWith("4")) {
            return false;
        }
        String errorCode = extractOpenAiErrorCode(t);
        if ("model_not_found".equals(errorCode)
                || "unsupported_model".equals(errorCode)
                || "invalid_model".equals(errorCode)
                || "unsupported_endpoint".equals(errorCode)) {
            return true;
        }
        String body = extractResponseBody(t).toLowerCase();
        return body.contains("does not support")
                || body.contains("not support")
                || body.contains("unsupported")
                || body.contains("model")
                && body.contains("not found")
                || body.contains("/v1/responses")
                || body.contains("/v1/chat/completions");
    }

    private String extractOpenAiErrorCode(Throwable t) {
        String body = extractResponseBody(t);
        if (isBlank(body)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String code = node.path("error").path("code").asText("");
            return code == null ? "" : code.trim().toLowerCase();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractResponseBody(Throwable t) {
        if (t instanceof WebClientResponseException e) {
            return e.getResponseBodyAsString();
        }
        Throwable root = rootCause(t);
        if (root instanceof WebClientResponseException e) {
            return e.getResponseBodyAsString();
        }
        return "";
    }

    private String normalizeEndpointMode(String mode) {
        if (isBlank(mode)) {
            return ENDPOINT_MODE_CHAT_COMPLETIONS;
        }
        String normalized = mode.trim().toLowerCase();
        if ("responses".equals(normalized)) {
            return ENDPOINT_MODE_RESPONSES;
        }
        return ENDPOINT_MODE_CHAT_COMPLETIONS;
    }

    private int extractDraftChars(String userPrompt) {
        if (isBlank(userPrompt)) {
            return 0;
        }
        String markerStart = "[DRAFT_TEXT] <<<";
        String markerEnd = ">>> [/DRAFT_TEXT]";
        int start = userPrompt.indexOf(markerStart);
        if (start < 0) {
            return 0;
        }
        int contentStart = start + markerStart.length();
        int end = userPrompt.indexOf(markerEnd, contentStart);
        if (end <= contentStart) {
            return 0;
        }
        return Math.max(0, end - contentStart);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long resolveEffectiveOverallTimeoutMs(OpenAiClientConfig cfg) {
        long responseTimeout = Math.max(1, cfg.getResponseTimeoutMs());
        int maxRetries = Math.max(0, cfg.getMaxRetries());
        long maxBackoff = Math.max(0, cfg.getMaxBackoffMs());

        // Ensure overall timeout can cover all attempts + retry backoff windows.
        long minimumNeeded = responseTimeout * (maxRetries + 1L) + maxBackoff * maxRetries + 2000L;
        long configured = Math.max(1, cfg.getOverallTimeoutMs());
        if (configured < minimumNeeded) {
            log.warn("OpenAI overall timeout too small for retry window; auto-adjusting configuredOverallTimeoutMs={} -> effectiveOverallTimeoutMs={} (responseTimeoutMs={}, maxRetries={}, maxBackoffMs={})",
                    configured, minimumNeeded, responseTimeout, maxRetries, maxBackoff);
            return minimumNeeded;
        }
        return configured;
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            int statusCode = e.getStatusCode().value();
            // NOTE: cleaned corrupted comment (encoding issue).
            return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
        }
        // NOTE: cleaned corrupted comment (encoding issue).
        Throwable root = rootCause(throwable);
        return (root instanceof TimeoutException || 
                root.getClass().getName().contains("Timeout")) ||
               throwable instanceof WebClientRequestException ||
               root instanceof WebClientRequestException;
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private Throwable rootCause(Throwable t) {
        if (t == null) {
            return null;
        }
        Throwable cause = t;
        int maxDepth = 10;
        int depth = 0;
        while (cause.getCause() != null && cause.getCause() != cause && depth < maxDepth) {
            cause = cause.getCause();
            depth++;
        }
        return cause;
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     * - 401 -> AUTH_ERROR
     * NOTE: cleaned corrupted comment (encoding issue).
     * - 5xx -> UPSTREAM_ERROR
     * NOTE: cleaned corrupted comment (encoding issue).
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private String classifyError(Throwable t) {
        if (t == null) {
            return "UNKNOWN";
        }
        // dev/local 婵☆垪鍓濈€?429
        if (t instanceof TooManyRequests) {
            return "UPSTREAM_ERROR";
        }
        Throwable root = rootCause(t);
        if (root instanceof TooManyRequests) {
            return "UPSTREAM_ERROR";
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (t instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) t;
            int status = e.getStatusCode().value();
            if (status == 401) {
                return "AUTH_ERROR";
            } else if (status >= 500) {
                return "UPSTREAM_ERROR";
            } else if (status >= 400) {
                return "CLIENT_ERROR";
            }
        }
        
        // 婵☆偀鍋撻柡?rootCause
        if (root == null) {
            root = t;
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (root instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) root;
            int status = e.getStatusCode().value();
            if (status == 401) {
                return "AUTH_ERROR";
            } else if (status >= 500) {
                return "UPSTREAM_ERROR";
            } else if (status >= 400) {
                return "CLIENT_ERROR";
            }
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (root instanceof TimeoutException || root.getClass().getName().contains("Timeout")) {
            return "TIMEOUT";
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (root instanceof UnknownHostException) {
            return "NETWORK_ERROR"; // NOTE: cleaned corrupted comment (encoding issue).
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (root instanceof SSLException || root.getClass().getName().contains("SSL")) {
            return "NETWORK_ERROR"; // NOTE: cleaned corrupted comment (encoding issue).
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (t instanceof WebClientRequestException || root instanceof WebClientRequestException ||
            root.getClass().getName().contains("Network") ||
            root.getClass().getName().contains("Connection")) {
            return "NETWORK_ERROR";
        }
        
        return "UNKNOWN";
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private String extractHttpStatus(Throwable t) {
        if (t instanceof TooManyRequests) {
            return "429";
        }
        if (t instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) t;
            return String.valueOf(e.getStatusCode().value());
        }
        // 婵☆偀鍋撻柡?rootCause
        Throwable root = rootCause(t);
        if (root instanceof TooManyRequests) {
            return "429";
        }
        if (root instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) root;
            return String.valueOf(e.getStatusCode().value());
        }
        return null;
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private String extractOpenAiRequestId(Throwable t) {
        if (t instanceof TooManyRequests) {
            return "debug";
        }
        Throwable root = rootCause(t);
        if (root instanceof TooManyRequests) {
            return "debug";
        }
        WebClientResponseException responseException = null;
        if (t instanceof WebClientResponseException) {
            responseException = (WebClientResponseException) t;
        } else if (root instanceof WebClientResponseException) {
            responseException = (WebClientResponseException) root;
        }
        
        if (responseException != null) {
            try {
                // NOTE: cleaned corrupted comment (encoding issue).
                HttpHeaders headers = responseException.getHeaders();
                if (headers != null) {
                    String requestId = headers.getFirst("x-request-id");
                    if (requestId == null || requestId.isBlank()) {
                        requestId = headers.getFirst("X-Request-Id");
                    }
                    if (requestId == null || requestId.isBlank()) {
                        requestId = headers.getFirst("request-id");
                    }
                    return requestId;
                }
            } catch (Exception ignored) {
                // NOTE: cleaned corrupted comment (encoding issue).
            }
        }
        return null;
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private String safeMsg(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String msg = throwable.getMessage();
        if (msg == null) {
            return "";
        }
        
        // NOTE: cleaned corrupted comment (encoding issue).
        msg = msg.replaceAll("sk-[a-zA-Z0-9]+", "sk-***");
        msg = msg.replaceAll("OPENAI_API_KEY", "***API_KEY***");
        
        // NOTE: cleaned corrupted comment (encoding issue).
        if (msg.length() > 300) {
            msg = msg.substring(0, 297) + "...";
        }
        
        return msg;
    }

    private void logPromptPayload(String traceId, ChatRequest request, String endpointMode) {
        if (request == null || request.getMessages() == null) {
            return;
        }
        List<Message> messages = request.getMessages();
        String lastSystem = "";
        String lastUser = "";
        int systemCount = 0;
        int userCount = 0;

        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String role = m.getRole() == null ? "" : m.getRole();
            String content = m.getContent() == null ? "" : m.getContent();
            if ("system".equals(role)) {
                systemCount++;
                lastSystem = content;
            } else if ("user".equals(role)) {
                userCount++;
                lastUser = content;
            }

            log.debug("OpenAI message traceId={} index={} role={} contentLength={} contentPreview={}",
                    traceId, i, role, content.length(), previewForLog(content, 120));
        }
        String payloadCanonical = canonicalMessages(messages);
        String payloadSha256 = sha256Hex(payloadCanonical);
        String systemSha256 = sha256Hex(lastSystem);
        String userSha256 = sha256Hex(lastUser);
        int payloadLength = payloadCanonical.length();

        log.info("OpenAI prompt traceId={} endpoint={} model={} messagesCount={} systemCount={} userCount={} systemPromptLength={} userPromptLength={} payloadLength={} systemPromptSha256={} userPromptSha256={} payloadSha256={}",
                traceId, endpointMode, request.getModel(), messages.size(), systemCount, userCount, lastSystem.length(), lastUser.length(),
                payloadLength, systemSha256, userSha256, payloadSha256);
        if (systemCount > 1 || userCount > 1) {
            log.warn("OpenAI prompt traceId={} multiple system/user detected systemCount={} userCount={}",
                    traceId, systemCount, userCount);
        }
        if (promptRawLogEnabled) {
            log.info("OpenAI prompt raw traceId={} role=system content=\n{}", traceId, limitForRawLog(redactForLog(lastSystem)));
            log.info("OpenAI prompt raw traceId={} role=user content=\n{}", traceId, limitForRawLog(redactForLog(lastUser)));
        }
        log.debug("OpenAI system prompt (last) traceId={} content={}", traceId, redactForLog(lastSystem));
        log.debug("OpenAI user prompt (last) traceId={} content={}", traceId, redactForLog(lastUser));
        log.debug("OpenAI messages payload traceId={} payload={}", traceId, formatMessagesForLog(messages));
    }

    private String formatMessagesForLog(List<Message> messages) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String role = m.getRole() == null ? "" : m.getRole();
            String content = m.getContent() == null ? "" : m.getContent();
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{index=")
                    .append(i)
                    .append(", role=\"")
                    .append(role)
                    .append("\", contentLength=")
                    .append(content.length())
                    .append(", contentPreview=\"")
                    .append(previewForLog(content, 120))
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String previewForLog(String content, int maxLen) {
        String normalized = redactForLog(content).replace("\r\n", "\n").replace("\n", "\\n");
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    private String redactForLog(String content) {
        if (content == null) {
            return "";
        }
        String redacted = content;
        redacted = redacted.replaceAll("sk-[a-zA-Z0-9]+", "sk-***");
        redacted = redacted.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        return redacted;
    }

    private String limitForRawLog(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= promptRawLogMaxChars) {
            return content;
        }
        return content.substring(0, promptRawLogMaxChars)
                + "\n...[truncated, totalChars=" + content.length() + "]";
    }

    private String canonicalMessages(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            String role = m.getRole() == null ? "" : m.getRole();
            String content = m.getContent() == null ? "" : m.getContent();
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i)
                    .append('|')
                    .append(role)
                    .append('|')
                    .append(content);
        }
        return sb.toString();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private long calculateBackoff(int attempt) {
        long baseBackoff = config.getInitialBackoffMs() * (1L << (attempt - 1));
        long backoff = Math.min(baseBackoff, config.getMaxBackoffMs());
        // NOTE: cleaned corrupted comment (encoding issue).
        long jitter = (long) (backoff * 0.2 * (RANDOM.nextDouble() * 2 - 1));
        return Math.max(0, backoff + jitter);
    }

    /**
     * NOTE: cleaned corrupted comment (encoding issue).
     */
    private String sanitizeError(String errorBody) {
        if (errorBody == null) return "";
        // NOTE: cleaned corrupted comment (encoding issue).
        return errorBody.replaceAll("sk-[a-zA-Z0-9]+", "sk-***");
    }

    private boolean isConnectTimeout(Throwable t) {
        if (t == null) return false;
        String cls = t.getClass().getName();
        return cls.contains("ConnectTimeoutException") || cls.contains("ReadTimeoutException");
    }

    private record OpenAiCallResult(String content, int payloadBytes, boolean parseSuccess) {
    }

    // Request/Response DTOs
    private static class ChatRequest {
        private String model;
        private List<Message> messages;
        private Double temperature;
        private Map<String, String> responseFormat;
        private Integer maxTokens;

        public ChatRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
            this.temperature = 0.2d;
            this.responseFormat = Map.of("type", "json_object");
            this.maxTokens = 4096;
        }

        @JsonProperty("model")
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        @JsonProperty("messages")
        public List<Message> getMessages() { return messages; }
        public void setMessages(List<Message> messages) { this.messages = messages; }

        @JsonProperty("temperature")
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }

        @JsonProperty("response_format")
        public Map<String, String> getResponseFormat() { return responseFormat; }
        public void setResponseFormat(Map<String, String> responseFormat) { this.responseFormat = responseFormat; }

        @JsonProperty("max_tokens")
        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    }

    private static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        @JsonProperty("role")
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        @JsonProperty("content")
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    private static class ChatResponse {
        private List<Choice> choices;

        @JsonProperty("choices")
        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }
    }

    private static class Choice {
        private Message message;

        @JsonProperty("message")
        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }

    private static class ResponsesRequest {
        private String model;
        private List<ResponseInputItem> input;
        private Double temperature;
        private Integer maxOutputTokens;

        public ResponsesRequest(String model, List<ResponseInputItem> input) {
            this.model = model;
            this.input = input;
            this.temperature = 0.2d;
            this.maxOutputTokens = 4096;
        }

        @JsonProperty("model")
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        @JsonProperty("input")
        public List<ResponseInputItem> getInput() { return input; }
        public void setInput(List<ResponseInputItem> input) { this.input = input; }

        @JsonProperty("temperature")
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }

        @JsonProperty("max_output_tokens")
        public Integer getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    }

    private static class ResponseInputItem {
        private String role;
        private List<ResponseContentItem> content;

        public ResponseInputItem(String role, List<ResponseContentItem> content) {
            this.role = role;
            this.content = content;
        }

        @JsonProperty("role")
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        @JsonProperty("content")
        public List<ResponseContentItem> getContent() { return content; }
        public void setContent(List<ResponseContentItem> content) { this.content = content; }
    }

    private static class ResponseContentItem {
        private String type;
        private String text;

        public ResponseContentItem(String type, String text) {
            this.type = type;
            this.text = text;
        }

        @JsonProperty("type")
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        @JsonProperty("text")
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}



