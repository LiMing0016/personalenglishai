package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.assistant.AssistantOpenAiClient;
import com.personalenglishai.backend.ai.assistant.AssistantOpenAiResponse;
import com.personalenglishai.backend.ai.assistant.AssistantOpenAiException;
import com.personalenglishai.backend.ai.assistant.AssistantResponseRequest;
import com.personalenglishai.backend.ai.assistant.AssistantToolCall;
import com.personalenglishai.backend.ai.assistant.AssistantToolDefinition;
import com.personalenglishai.backend.ai.assistant.AssistantToolOutput;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import com.personalenglishai.backend.service.subscription.AiUsageRecorder;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;

/**
 * NOTE: cleaned corrupted comment (encoding issue).
 * NOTE: cleaned corrupted comment (encoding issue).
 */
@Component
public class OpenAiClient implements AssistantOpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String ENDPOINT_MODE_CHAT_COMPLETIONS = "chat_completions";
    private static final String ENDPOINT_MODE_RESPONSES = "responses";
    private static final String PROMPT_VERSION = "v1";
    private static final String VOCABULARY_MARKDOWN_PROMPT_PREFIX =
            "以下 JSON 是可信的卡片核心与来源上下文：";
    private static final String VOCABULARY_THEME_PURPOSE_TAG = "<theme-purpose>";
    private static final Random RANDOM = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AiProviderSelection providerSelection;
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
    private final String activeProvider;
    private final String endpointMode;
    private final String fallbackModel;
    private final boolean aiPromptDebugEnabled;
    private final boolean promptRawLogEnabled;
    private final int promptRawLogMaxChars;
    private final long effectiveOverallTimeoutMs;
    private final ReactorClientHttpConnector clientConnector;
    private final ConcurrentMap<String, WebClient> providerWebClients = new ConcurrentHashMap<>();
    @Autowired(required = false)
    private AiUsageRecorder aiUsageRecorder;

    // Per-call overrides (thread-local would be safer but this is simpler for single-threaded use)
    private volatile Double overrideTemperature;
    private volatile Integer overrideMaxTokens;
    private volatile String overrideModel;

    public OpenAiClient(
            AiProviderSelection providerSelection,
            @Value("${spring.profiles.active:}") String activeProfile,
            @Value("${AI_ENDPOINT_MODE:" + ENDPOINT_MODE_CHAT_COMPLETIONS + "}") String endpointMode,
            @Value("${AI_FALLBACK_MODEL:" + DEFAULT_MODEL + "}") String fallbackModel,
            @Value("${AI_PROMPT_DEBUG:false}") boolean aiPromptDebugEnabled,
            @Value("${ai.prompt.log-raw-enabled:false}") boolean promptRawLogEnabled,
            @Value("${ai.prompt.log-raw-max-chars:12000}") int promptRawLogMaxChars,
            OpenAiClientConfig config) {
        this.providerSelection = providerSelection;
        this.config = config;
        this.activeProfile = activeProfile == null ? "" : activeProfile.toLowerCase();
        this.isDevOrLocal = isDevOrLocalProfile(activeProfile);
        this.endpointMode = normalizeEndpointMode(endpointMode);
        this.aiPromptDebugEnabled = aiPromptDebugEnabled;
        this.promptRawLogEnabled = promptRawLogEnabled;
        this.promptRawLogMaxChars = Math.max(2000, promptRawLogMaxChars);
        this.effectiveOverallTimeoutMs = resolveEffectiveOverallTimeoutMs(config);
        this.circuitBreaker = new CircuitBreaker(
                config.getCircuitBreakerFailureThreshold(),
                config.getCircuitBreakerWindowMs(),
                config.getCircuitBreakerRecoveryMs()
        );

        AiProviderSelection.SelectedProvider activeSelection = providerSelection.resolve(null);
        this.activeProvider = activeSelection.provider();
        this.apiKey = activeSelection.apiKey();
        this.baseUrl = normalizeProviderBaseUrl(firstNonBlank(
                activeSelection.baseUrl(),
                config.getBaseUrl(),
                "https://api.openai.com"
        ));
        this.model = isBlank(activeSelection.model()) ? DEFAULT_MODEL : activeSelection.model().trim();
        this.fallbackModel = isBlank(fallbackModel) ? this.model : fallbackModel.trim();

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

        this.clientConnector = new ReactorClientHttpConnector(httpClient);
        this.webClient = buildWebClient(this.baseUrl, this.apiKey);
    }
    public String getModel() { return model; }

    public String resolveModel(String provider) {
        return resolveSelectedProvider(provider).model();
    }

    public String generateImageWithProvider(String provider, String prompt, String traceId) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        if (normalizedPrompt.isEmpty()) {
            return null;
        }

        AiProviderSelection.SelectedProvider selectedProvider = resolveImageProvider(provider);
        if (selectedProvider == null || isBlank(selectedProvider.imageModel())) {
            log.warn("OpenAI image generation skipped traceId={} provider={} reason=no-image-model-configured", traceId, provider);
            return null;
        }

        try {
            return generateImage(selectedProvider, normalizedPrompt, traceId);
        } catch (Exception primaryError) {
            log.warn("OpenAI image generation failed traceId={} provider={} model={} reason={}",
                    traceId,
                    selectedProvider.provider(),
                    selectedProvider.imageModel(),
                    safeMsg(primaryError));

            if (!"openai".equalsIgnoreCase(selectedProvider.provider())) {
                AiProviderSelection.SelectedProvider fallbackProvider = resolveImageProvider("openai");
                if (fallbackProvider != null
                        && !isBlank(fallbackProvider.imageModel())
                        && !"openai".equalsIgnoreCase(selectedProvider.provider())) {
                    try {
                        log.info("OpenAI image generation fallback traceId={} fromProvider={} toProvider=openai",
                                traceId,
                                selectedProvider.provider());
                        return generateImage(fallbackProvider, normalizedPrompt, traceId + "-fallback");
                    } catch (Exception fallbackError) {
                        log.warn("OpenAI image generation fallback failed traceId={} provider=openai model={} reason={}",
                                traceId,
                                fallbackProvider.imageModel(),
                                safeMsg(fallbackError));
                    }
                }
            }
            return null;
        }
    }

    public String callVisionWithProvider(String provider,
                                         String systemPrompt,
                                         String userPrompt,
                                         String imageDataUrl,
                                         String traceId) {
        long startTime = System.currentTimeMillis();
        String normalizedImageDataUrl = trimToNull(imageDataUrl);
        if (normalizedImageDataUrl == null) {
            return null;
        }

        int inputLength = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        AtomicInteger attemptCounter = new AtomicInteger(1);
        int draftChars = extractDraftChars(userPrompt);
        boolean fallbackUsed = false;
        boolean parseSuccess = false;
        int payloadBytes = 0;
        AiProviderSelection.SelectedProvider selectedProvider = resolveSelectedProvider(provider);
        WebClient targetWebClient = getWebClient(selectedProvider);
        String effectiveModel = overrideModel != null ? overrideModel : selectedProvider.model();

        Retry retrySpec = Retry.backoff(config.getMaxRetries(), Duration.ofMillis(config.getInitialBackoffMs()))
                .maxBackoff(Duration.ofMillis(config.getMaxBackoffMs()))
                .filter(this::shouldRetry)
                .doBeforeRetry(retrySignal -> attemptCounter.incrementAndGet());

        try {
            OpenAiCallResult callResult = callVisionChatCompletions(
                    selectedProvider,
                    targetWebClient,
                    effectiveModel,
                    systemPrompt,
                    userPrompt,
                    normalizedImageDataUrl,
                    traceId,
                    retrySpec
            );

            String output = callResult.content();
            payloadBytes = callResult.payloadBytes();
            parseSuccess = callResult.parseSuccess();

            circuitBreaker.recordSuccess();
            long latency = System.currentTimeMillis() - startTime;
            log.info("OpenAI vision call succeeded traceId={} provider={} attempt={} latencyMs={} httpStatus=200 inputLength={} outputLength={}",
                    traceId, selectedProvider.provider(), attemptCounter.get(), latency, inputLength, output.length());
            log.info("OpenAI vision call metrics traceId={} provider={} baseUrl={} prompt_version={} endpoint={} model={} payload_bytes={} input_chars={} draft_chars={} response_ms={} parse_success={} fallback_used={}",
                    traceId, selectedProvider.provider(), selectedProvider.baseUrl(), PROMPT_VERSION, "vision_chat_completions", effectiveModel, payloadBytes, inputLength, draftChars, latency, parseSuccess, fallbackUsed);
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
            log.error("OpenAI vision call failed traceId={} provider={} baseUrl={} attempt={} latencyMs={} errorType={} httpStatus={} rootCauseClass={} rootCauseMsg={} inputLength={} fallbackUsed={} endpoint={} model={}{} responseBody={}",
                    traceId, selectedProvider.provider(), selectedProvider.baseUrl(), attemptCounter.get(), latency, errorType, httpStatus != null ? httpStatus : "",
                    rootCauseClass, rootCauseMsg, inputLength, fallbackUsed, "vision_chat_completions", effectiveModel,
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

    public String callWithProvider(String provider, String systemPrompt, String userPrompt, String traceId) {
        return callInternal(provider, systemPrompt, userPrompt, traceId, null);
    }

    public String callWithProvider(String provider, String systemPrompt, String userPrompt, String traceId,
                                   Double temperature, Integer maxTokens) {
        this.overrideTemperature = temperature;
        this.overrideMaxTokens = maxTokens;
        try {
            return callInternal(provider, systemPrompt, userPrompt, traceId, null);
        } finally {
            this.overrideTemperature = null;
            this.overrideMaxTokens = null;
        }
    }

    public String callWithProvider(String provider, String systemPrompt, String userPrompt, String traceId,
                                   String modelOverride, Double temperature, Integer maxTokens) {
        this.overrideModel = modelOverride;
        this.overrideTemperature = temperature;
        this.overrideMaxTokens = maxTokens;
        try {
            return callInternal(provider, systemPrompt, userPrompt, traceId, null);
        } finally {
            this.overrideModel = null;
            this.overrideTemperature = null;
            this.overrideMaxTokens = null;
        }
    }

    public String callWithProvider(String provider, String systemPrompt, String userPrompt, String traceId, String xDebugFail) {
        return callInternal(provider, systemPrompt, userPrompt, traceId, xDebugFail);
    }

    public String call(String systemPrompt, String userPrompt) {
        return callInternal(null, systemPrompt, userPrompt, null, null);
    }

    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId) {
        return callInternal(null, systemPrompt, userPrompt, traceId, null);
    }

    /**
     * Call with custom temperature and maxTokens.
     */
    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId,
                                   Double temperature, Integer maxTokens) {
        return callWithProvider(null, systemPrompt, userPrompt, traceId, temperature, maxTokens);
    }

    /**
     * Call with custom model, temperature and maxTokens.
     */
    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId,
                                   String modelOverride, Double temperature, Integer maxTokens) {
        return callWithProvider(null, systemPrompt, userPrompt, traceId, modelOverride, temperature, maxTokens);
    }

    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId, String xDebugFail) {
        return callInternal(null, systemPrompt, userPrompt, traceId, xDebugFail);
    }

    public String callStructuredWithTraceId(
            String systemPrompt,
            String userPrompt,
            String traceId,
            String schemaName,
            JsonNode schema,
            Double temperature,
            Integer maxTokens) {
        if (isBlank(schemaName) || schema == null || !schema.isObject()) {
            throw new IllegalArgumentException("Structured output schema is required");
        }
        return callInternal(
                null, systemPrompt, userPrompt, traceId, null,
                new StructuredOutputConfig(schemaName.trim(), schema.deepCopy(), temperature, maxTokens));
    }

    private String callInternal(String provider, String systemPrompt, String userPrompt, String traceId, String xDebugFail) {
        return callInternal(provider, systemPrompt, userPrompt, traceId, xDebugFail, null);
    }

    private String callInternal(
            String provider,
            String systemPrompt,
            String userPrompt,
            String traceId,
            String xDebugFail,
            StructuredOutputConfig structuredOutput) {
        long startTime = System.currentTimeMillis();
        int inputLength = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        AtomicInteger attemptCounter = new AtomicInteger(1);
        int draftChars = extractDraftChars(userPrompt);
        boolean fallbackUsed = false;
        boolean parseSuccess = false;
        int payloadBytes = 0;
        AiProviderSelection.SelectedProvider selectedProvider = resolveSelectedProvider(provider);
        WebClient targetWebClient = getWebClient(selectedProvider);
        String effectiveEndpoint = endpointMode;
        String effectiveModel = overrideModel != null ? overrideModel : selectedProvider.model();
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
                callResult = callByMode(selectedProvider, targetWebClient, effectiveEndpoint, effectiveModel,
                        systemPrompt, userPrompt, traceId, retrySpec, structuredOutput);
            } catch (Exception e) {
                if (shouldFallbackFromResponses(effectiveEndpoint, e)) {
                    fallbackUsed = true;
                    effectiveEndpoint = ENDPOINT_MODE_CHAT_COMPLETIONS;
                    effectiveModel = fallbackModel;
                    log.warn("OpenAI fallback engaged traceId={} provider={} fromEndpoint={} fromModel={} toEndpoint={} toModel={} reason={} errorCode={} httpStatus={}",
                            traceId,
                            selectedProvider.provider(),
                            endpointMode,
                            selectedProvider.model(),
                            effectiveEndpoint,
                            effectiveModel,
                            safeMsg(e),
                            extractOpenAiErrorCode(e),
                            extractHttpStatus(e));
                    callResult = callByMode(selectedProvider, targetWebClient, effectiveEndpoint, effectiveModel,
                            systemPrompt, userPrompt, traceId, retrySpec, structuredOutput);
                } else {
                    throw e;
                }
            }

            output = callResult.content();
            payloadBytes = callResult.payloadBytes();
            parseSuccess = callResult.parseSuccess();

            circuitBreaker.recordSuccess();
            long latency = System.currentTimeMillis() - startTime;
            log.info("OpenAI call succeeded traceId={} provider={} attempt={} latencyMs={} httpStatus=200 inputLength={} outputLength={}",
                    traceId, selectedProvider.provider(), attemptCounter.get(), latency, inputLength, output.length());
            log.info("OpenAI call metrics traceId={} provider={} baseUrl={} prompt_version={} endpoint={} model={} payload_bytes={} input_chars={} draft_chars={} response_ms={} parse_success={} fallback_used={}",
                    traceId, selectedProvider.provider(), selectedProvider.baseUrl(), PROMPT_VERSION, effectiveEndpoint, effectiveModel, payloadBytes, inputLength, draftChars, latency, parseSuccess, fallbackUsed);
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
            log.error("OpenAI call failed traceId={} provider={} baseUrl={} attempt={} latencyMs={} errorType={} httpStatus={} rootCauseClass={} rootCauseMsg={} inputLength={} fallbackUsed={} endpoint={} model={}{} responseBody={}",
                    traceId, selectedProvider.provider(), selectedProvider.baseUrl(), attemptCounter.get(), latency, errorType, httpStatus != null ? httpStatus : "",
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

    @Override
    public AssistantOpenAiResponse createResponse(AssistantResponseRequest request) {
        String targetModel = isBlank(request.model()) ? model : request.model().trim();
        Retry retrySpec = Retry.backoff(config.getMaxRetries(), Duration.ofMillis(config.getInitialBackoffMs()))
                .maxBackoff(Duration.ofMillis(config.getMaxBackoffMs()))
                .filter(this::shouldRetry);

        ObjectNode payload = buildAssistantResponsesPayload(targetModel, request);
        int payloadBytes = logFinalPayload(
                null,
                ENDPOINT_MODE_RESPONSES,
                targetModel,
                payload,
                estimateAssistantInputChars(request),
                0
        );

        JsonNode responseNode;
        try {
            responseNode = webClient.post()
                    .uri("/v1/responses")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                    .retryWhen(retrySpec)
                    .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                    .block();
        } catch (WebClientResponseException e) {
            String responseBody = sanitizeError(extractResponseBody(e));
            String errorCode = extractOpenAiErrorCode(e);
            String errorParam = extractOpenAiErrorParam(e);
            log.error("OpenAI assistant response failed model={} httpStatus={} errorCode={} errorParam={} payloadBytes={} responseBody={}",
                    targetModel,
                    e.getStatusCode().value(),
                    errorCode,
                    errorParam,
                    payloadBytes,
                    responseBody);
            throw new AssistantOpenAiException(
                    e.getStatusCode().value(),
                    errorCode,
                    errorParam,
                    responseBody,
                    e
            );
        }

        String responseId = responseNode == null ? null : responseNode.path("id").asText(null);
        String outputText = extractResponsesText(responseNode);
        List<AssistantToolCall> toolCalls = extractAssistantToolCalls(responseNode);

        if (toolCalls.isEmpty() && isBlank(outputText)) {
            throw new RuntimeException("Empty assistant response from OpenAI responses API");
        }
        log.info("OpenAI assistant response parsed model={} payloadBytes={} responseId={} toolCalls={} outputLength={}",
                targetModel,
                payloadBytes,
                responseId,
                toolCalls.size(),
                outputText == null ? 0 : outputText.length());
        return new AssistantOpenAiResponse(responseId, outputText, toolCalls);
    }

    public OpenAiResponsesTextResult createTextResponse(OpenAiResponsesTextRequest request) {
        AiProviderSelection.SelectedProvider selectedProvider = resolveSelectedProvider(request.provider());
        WebClient targetWebClient = getWebClient(selectedProvider);
        String targetModel = isBlank(request.model()) ? selectedProvider.model() : request.model().trim();
        Retry retrySpec = Retry.backoff(config.getMaxRetries(), Duration.ofMillis(config.getInitialBackoffMs()))
                .maxBackoff(Duration.ofMillis(config.getMaxBackoffMs()))
                .filter(this::shouldRetry);

        if (!supportsResponses(selectedProvider.provider())) {
            OpenAiCallResult fallbackResult = callChatCompletionsForText(
                    selectedProvider,
                    targetWebClient,
                    targetModel,
                    request.instructions(),
                    request.input(),
                    retrySpec,
                    request.maxOutputTokens()
            );
            log.info("OpenAI text response fallback provider={} baseUrl={} model={} payloadBytes={} outputLength={}",
                    selectedProvider.provider(),
                    selectedProvider.baseUrl(),
                    targetModel,
                    fallbackResult.payloadBytes(),
                    fallbackResult.content().length());
            return new OpenAiResponsesTextResult(
                    null,
                    fallbackResult.content(),
                    null,
                    null,
                    fallbackResult.payloadBytes()
            );
        }

        ObjectNode payload = buildTextResponsesPayload(request);
        int payloadBytes = logFinalPayload(
                null,
                ENDPOINT_MODE_RESPONSES,
                targetModel,
                payload,
                estimateTextResponseInputChars(request),
                extractDraftChars(request.input())
        );

        JsonNode responseNode;
        try {
            responseNode = targetWebClient.post()
                    .uri("/v1/responses")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                    .retryWhen(retrySpec)
                    .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                    .block();
        } catch (WebClientResponseException e) {
            String responseBody = sanitizeError(extractResponseBody(e));
            String errorCode = extractOpenAiErrorCode(e);
            String errorParam = extractOpenAiErrorParam(e);
            log.error("OpenAI text response failed provider={} baseUrl={} model={} httpStatus={} errorCode={} errorParam={} payloadBytes={} responseBody={}",
                    selectedProvider.provider(),
                    selectedProvider.baseUrl(),
                    targetModel,
                    e.getStatusCode().value(),
                    errorCode,
                    errorParam,
                    payloadBytes,
                    responseBody);
            throw new OpenAiResponsesException(
                    e.getStatusCode().value(),
                    errorCode,
                    errorParam,
                    responseBody,
                    e
            );
        }

        String responseId = responseNode == null ? null : responseNode.path("id").asText(null);
        String outputText = extractResponsesText(responseNode);
        if (isBlank(outputText)) {
            throw new RuntimeException("Empty text response from OpenAI responses API");
        }
        JsonNode usage = responseNode.path("usage");
        Integer inputTokens = usage.path("input_tokens").isInt() ? usage.path("input_tokens").asInt() : null;
        Integer cachedTokens = usage.path("input_tokens_details").path("cached_tokens").isInt()
                ? usage.path("input_tokens_details").path("cached_tokens").asInt() : null;
        Integer outputTokens = usage.path("output_tokens").isInt() ? usage.path("output_tokens").asInt() : null;
        Integer reasoningTokens = usage.path("output_tokens_details").path("reasoning_tokens").isInt()
                ? usage.path("output_tokens_details").path("reasoning_tokens").asInt() : null;
        Integer totalTokens = sumTokens(inputTokens, outputTokens, reasoningTokens);
        recordUsage(selectedProvider.provider(), targetModel, responseId, inputTokens, cachedTokens, outputTokens, reasoningTokens, totalTokens);
        log.info("OpenAI text response parsed provider={} baseUrl={} model={} payloadBytes={} responseId={} inputTokens={} cachedTokens={} outputTokens={} reasoningTokens={} outputLength={}",
                selectedProvider.provider(),
                selectedProvider.baseUrl(),
                targetModel,
                payloadBytes,
                responseId,
                inputTokens,
                cachedTokens,
                outputTokens,
                reasoningTokens,
                outputText.length());
        return new OpenAiResponsesTextResult(responseId, outputText, inputTokens, cachedTokens, outputTokens, reasoningTokens, totalTokens, payloadBytes);
    }

    private OpenAiCallResult callChatCompletionsForText(AiProviderSelection.SelectedProvider selectedProvider,
                                                        WebClient targetWebClient,
                                                        String model,
                                                        String instructions,
                                                        String input,
                                                        Retry retrySpec,
                                                        Integer maxOutputTokens) {
        ChatRequest request = new ChatRequest(model, List.of(
                new Message("system", instructions == null ? "" : instructions),
                new Message("user", input == null ? "" : input)
        ));
        request.setTemperature(normalizeChatCompletionsTemperature(
                selectedProvider,
                overrideTemperature != null ? overrideTemperature : request.getTemperature()
        ));
        if (overrideMaxTokens != null) {
            request.setMaxTokens(overrideMaxTokens);
        } else if (maxOutputTokens != null) {
            request.setMaxTokens(maxOutputTokens);
        }
        int payloadBytes = logFinalPayload(
                null,
                ENDPOINT_MODE_CHAT_COMPLETIONS,
                model,
                request,
                estimateTextResponseInputChars(new OpenAiResponsesTextRequest(null, model, instructions, input, null, null, null, false, request.getMaxTokens())),
                extractDraftChars(input)
        );

        ChatResponse response = targetWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                .retryWhen(retrySpec)
                .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                .block();

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new RuntimeException("Empty response from OpenAI-compatible chat completions API");
        }
        String content = response.getChoices().get(0).getMessage().getContent();
        if (isBlank(content)) {
            throw new RuntimeException("Empty content in OpenAI-compatible chat completions response");
        }
        recordUsage(selectedProvider.provider(), model, response.getId(), responseInputTokens(response), responseCachedInputTokens(response),
                responseOutputTokens(response), responseReasoningTokens(response), responseTotalTokens(response));
        return new OpenAiCallResult(content, payloadBytes, true);
    }

    private static boolean isDevOrLocalProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return false;
        }
        String p = profile.toLowerCase();
        return p.contains("dev") || p.contains("local");
    }

    private OpenAiCallResult callByMode(AiProviderSelection.SelectedProvider selectedProvider,
                                        WebClient targetWebClient,
                                        String endpointMode,
                                        String model,
                                        String systemPrompt,
                                        String userPrompt,
                                        String traceId,
                                        Retry retrySpec,
                                        StructuredOutputConfig structuredOutput) {
        if (ENDPOINT_MODE_RESPONSES.equals(endpointMode) && supportsResponses(selectedProvider.provider())) {
            return callResponses(targetWebClient, model, systemPrompt, userPrompt, traceId, retrySpec,
                    structuredOutput);
        }
        return callChatCompletions(selectedProvider, targetWebClient, model, systemPrompt, userPrompt,
                traceId, retrySpec, structuredOutput);
    }

    private OpenAiCallResult callChatCompletions(AiProviderSelection.SelectedProvider selectedProvider,
                                                 WebClient targetWebClient,
                                                 String model,
                                                 String systemPrompt,
                                                 String userPrompt,
                                                 String traceId,
                                                 Retry retrySpec,
                                                 StructuredOutputConfig structuredOutput) {
        Object request;
        if (structuredOutput == null) {
            ChatRequest chatRequest = new ChatRequest(model, List.of(
                    new Message("system", systemPrompt == null ? "" : systemPrompt),
                    new Message("user", userPrompt == null ? "" : userPrompt)
            ));
            chatRequest.setTemperature(normalizeChatCompletionsTemperature(
                    selectedProvider,
                    overrideTemperature != null ? overrideTemperature : chatRequest.getTemperature()
            ));
            if (overrideMaxTokens != null) chatRequest.setMaxTokens(overrideMaxTokens);
            request = chatRequest;
        } else {
            request = buildStructuredChatCompletionsPayload(
                    selectedProvider, model, systemPrompt, userPrompt,
                    structuredOutput.schemaName(), structuredOutput.schema(),
                    structuredOutput.temperature(), structuredOutput.maxTokens());
        }
        int inputChars = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        int draftChars = extractDraftChars(userPrompt);
        int payloadBytes = logFinalPayload(traceId, ENDPOINT_MODE_CHAT_COMPLETIONS, model, request, inputChars, draftChars);
        ChatRequest promptLogRequest = new ChatRequest(model, List.of(
                new Message("system", systemPrompt == null ? "" : systemPrompt),
                new Message("user", userPrompt == null ? "" : userPrompt)
        ));
        logPromptPayload(traceId, promptLogRequest, ENDPOINT_MODE_CHAT_COMPLETIONS);

        ChatResponse response = targetWebClient.post()
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
        recordUsage(selectedProvider.provider(), model, response.getId(), responseInputTokens(response), responseCachedInputTokens(response),
                responseOutputTokens(response), responseReasoningTokens(response), responseTotalTokens(response));
        return new OpenAiCallResult(content, payloadBytes, true);
    }

    private OpenAiCallResult callVisionChatCompletions(AiProviderSelection.SelectedProvider selectedProvider,
                                                       WebClient targetWebClient,
                                                       String model,
                                                       String systemPrompt,
                                                       String userPrompt,
                                                       String imageDataUrl,
                                                       String traceId,
                                                       Retry retrySpec) {
        ObjectNode request = buildVisionChatCompletionsPayload(
                selectedProvider,
                model,
                systemPrompt,
                userPrompt,
                imageDataUrl
        );
        int inputChars = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        int draftChars = extractDraftChars(userPrompt);
        int payloadBytes = logFinalPayload(traceId, ENDPOINT_MODE_CHAT_COMPLETIONS, model, request, inputChars, draftChars);

        ChatResponse response = targetWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                .retryWhen(retrySpec)
                .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                .block();

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new RuntimeException("Empty response from OpenAI vision API");
        }
        String content = response.getChoices().get(0).getMessage().getContent();
        if (isBlank(content)) {
            throw new RuntimeException("Empty content in OpenAI vision response");
        }
        recordUsage(selectedProvider.provider(), model, response.getId(), responseInputTokens(response), responseCachedInputTokens(response),
                responseOutputTokens(response), responseReasoningTokens(response), responseTotalTokens(response));
        return new OpenAiCallResult(content, payloadBytes, true);
    }

    private OpenAiCallResult callResponses(WebClient targetWebClient,
                                           String model,
                                           String systemPrompt,
                                           String userPrompt,
                                           String traceId,
                                           Retry retrySpec,
                                           StructuredOutputConfig structuredOutput) {
        Object request = structuredOutput == null
                ? new ResponsesRequest(model, List.of(
                        new ResponseInputItem("system", List.of(new ResponseContentItem(
                                "input_text", systemPrompt == null ? "" : systemPrompt))),
                        new ResponseInputItem("user", List.of(new ResponseContentItem(
                                "input_text", userPrompt == null ? "" : userPrompt)))))
                : buildStructuredResponsesPayload(
                        model, systemPrompt, userPrompt,
                        structuredOutput.schemaName(), structuredOutput.schema(),
                        structuredOutput.temperature(), structuredOutput.maxTokens());
        int inputChars = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        int draftChars = extractDraftChars(userPrompt);
        int payloadBytes = logFinalPayload(traceId, ENDPOINT_MODE_RESPONSES, model, request, inputChars, draftChars);

        JsonNode responseNode = targetWebClient.post()
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
        JsonNode usage = responseNode.path("usage");
        Integer inputTokens = intOrNull(usage.path("input_tokens"));
        Integer cachedTokens = intOrNull(usage.path("input_tokens_details").path("cached_tokens"));
        Integer outputTokens = intOrNull(usage.path("output_tokens"));
        Integer reasoningTokens = intOrNull(usage.path("output_tokens_details").path("reasoning_tokens"));
        Integer totalTokens = sumTokens(inputTokens, outputTokens, reasoningTokens);
        recordUsage("openai", model, responseNode.path("id").asText(null), inputTokens, cachedTokens, outputTokens, reasoningTokens, totalTokens);
        return new OpenAiCallResult(content, payloadBytes, true);
    }

    private ObjectNode buildTextResponsesPayload(OpenAiResponsesTextRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        String targetModel = isBlank(request.model()) ? model : request.model().trim();
        payload.put("model", targetModel);
        payload.put("store", request.store());
        if (!isBlank(request.instructions())) {
            payload.put("instructions", request.instructions());
        }
        payload.set("input", objectMapper.getNodeFactory().textNode(request.input() == null ? "" : request.input()));
        if (!isBlank(request.previousResponseId())) {
            payload.put("previous_response_id", request.previousResponseId());
        }
        if (!isBlank(request.promptCacheKey())) {
            payload.put("prompt_cache_key", request.promptCacheKey());
        }
        if (!isBlank(request.promptCacheRetention())) {
            payload.put("prompt_cache_retention", request.promptCacheRetention());
        }
        if (request.maxOutputTokens() != null) {
            payload.put("max_output_tokens", request.maxOutputTokens());
        }
        ObjectNode text = payload.putObject("text");
        text.putObject("format").put("type", "text");
        return payload;
    }

    private ObjectNode buildStructuredResponsesPayload(
            String model,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            JsonNode schema,
            Double temperature,
            Integer maxTokens) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        ArrayNode input = payload.putArray("input");
        addResponsesMessage(input, "system", systemPrompt);
        addResponsesMessage(input, "user", userPrompt);
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        if (maxTokens != null) {
            payload.put("max_output_tokens", maxTokens);
        }
        ObjectNode format = payload.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);
        return payload;
    }

    private ObjectNode buildStructuredChatCompletionsPayload(
            AiProviderSelection.SelectedProvider selectedProvider,
            String model,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            JsonNode schema,
            Double temperature,
            Integer maxTokens) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        ArrayNode messages = payload.putArray("messages");
        addChatMessage(messages, "system", systemPrompt);
        addChatMessage(messages, "user", userPrompt);
        payload.put("temperature", normalizeChatCompletionsTemperature(
                selectedProvider, temperature == null ? 0.2d : temperature));
        if (maxTokens != null) {
            payload.put("max_tokens", maxTokens);
        }
        ObjectNode responseFormat = payload.putObject("response_format");
        responseFormat.put("type", "json_schema");
        ObjectNode jsonSchema = responseFormat.putObject("json_schema");
        jsonSchema.put("name", schemaName);
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);
        return payload;
    }

    private void addResponsesMessage(ArrayNode input, String role, String value) {
        ObjectNode message = input.addObject();
        message.put("role", role);
        ObjectNode content = message.putArray("content").addObject();
        content.put("type", "input_text");
        content.put("text", value == null ? "" : value);
    }

    private void addChatMessage(ArrayNode messages, String role, String value) {
        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.put("content", value == null ? "" : value);
    }

    private ObjectNode buildVisionChatCompletionsPayload(AiProviderSelection.SelectedProvider selectedProvider,
                                                         String model,
                                                         String systemPrompt,
                                                         String userPrompt,
                                                         String imageDataUrl) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);

        ArrayNode messages = payload.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt == null ? "" : systemPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");

        ObjectNode textItem = content.addObject();
        textItem.put("type", "text");
        textItem.put("text", userPrompt == null ? "" : userPrompt);

        ObjectNode imageItem = content.addObject();
        imageItem.put("type", "image_url");
        ObjectNode imageUrl = imageItem.putObject("image_url");
        imageUrl.put("url", imageDataUrl);

        payload.put("temperature", normalizeChatCompletionsTemperature(
                selectedProvider,
                overrideTemperature != null ? overrideTemperature : 0.2d
        ));
        payload.put("max_tokens", overrideMaxTokens != null ? overrideMaxTokens : 4096);

        ObjectNode responseFormat = payload.putObject("response_format");
        responseFormat.put("type", "json_object");
        return payload;
    }

    private ObjectNode buildAssistantResponsesPayload(String model, AssistantResponseRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("store", request.store());
        payload.put("tool_choice", request.tools() == null || request.tools().isEmpty() ? "none" : "auto");
        payload.put("parallel_tool_calls", true);
        if (!isBlank(request.instructions())) {
            payload.put("instructions", request.instructions());
        }
        if (!isBlank(request.previousResponseId())) {
            payload.put("previous_response_id", request.previousResponseId());
        }
        payload.set("input", buildAssistantInput(request));
        payload.set("tools", buildAssistantTools(request.tools()));
        payload.set("text", buildAssistantTextConfig());
        return payload;
    }

    private JsonNode buildAssistantInput(AssistantResponseRequest request) {
        List<AssistantToolOutput> toolOutputs = request.toolOutputs() == null ? List.of() : request.toolOutputs();
        if (toolOutputs.isEmpty()) {
            return isBlank(request.inputText())
                    ? objectMapper.createArrayNode()
                    : objectMapper.getNodeFactory().textNode(request.inputText());
        }

        ArrayNode input = objectMapper.createArrayNode();
        if (!isBlank(request.inputText())) {
            input.add(new ResponseInputItem(
                    "user",
                    List.of(new ResponseContentItem("input_text", request.inputText()))
            ).toJson(objectMapper));
        }
        for (AssistantToolOutput toolOutput : toolOutputs) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("type", "function_call_output");
            item.put("call_id", toolOutput.callId());
            item.put("output", toolOutput.outputJson());
            input.add(item);
        }
        return input;
    }

    private ArrayNode buildAssistantTools(List<AssistantToolDefinition> tools) {
        ArrayNode toolNodes = objectMapper.createArrayNode();
        if (tools == null) {
            return toolNodes;
        }
        for (AssistantToolDefinition tool : tools) {
            if (tool == null || isBlank(tool.name())) {
                continue;
            }
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("type", "function");
            toolNode.put("name", tool.name());
            if (!isBlank(tool.description())) {
                toolNode.put("description", tool.description());
            }
            toolNode.put("strict", true);
            try {
                JsonNode parameters = isBlank(tool.parametersJson())
                        ? objectMapper.createObjectNode()
                        : objectMapper.readTree(tool.parametersJson());
                toolNode.set("parameters", parameters);
            } catch (Exception e) {
                throw new IllegalArgumentException("invalid assistant tool schema: " + tool.name(), e);
            }
            toolNodes.add(toolNode);
        }
        return toolNodes;
    }

    private ObjectNode buildAssistantTextConfig() {
        ObjectNode text = objectMapper.createObjectNode();
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", "writing_assistant_response");
        format.put("strict", true);
        format.set("schema", buildAssistantResponseSchema());
        return text;
    }

    private ObjectNode buildAssistantResponseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("message").put("type", "string");

        ObjectNode summary = properties.putObject("summary");
        summary.put("type", "array");
        summary.putObject("items").put("type", "string");

        ObjectNode actions = properties.putObject("actions");
        actions.put("type", "array");
        ObjectNode actionItem = actions.putObject("items");
        actionItem.put("type", "object");
        ObjectNode actionProperties = actionItem.putObject("properties");
        actionProperties.putObject("type").put("type", "string");
        actionProperties.putObject("label").put("type", "string");
        ArrayNode textTypes = actionProperties.putObject("text").putArray("type");
        textTypes.add("string");
        textTypes.add("null");
        ArrayNode panelTypes = actionProperties.putObject("panel").putArray("type");
        panelTypes.add("string");
        panelTypes.add("null");
        ArrayNode actionRequired = actionItem.putArray("required");
        actionRequired.add("type");
        actionRequired.add("label");
        actionRequired.add("text");
        actionRequired.add("panel");
        actionItem.put("additionalProperties", false);

        ArrayNode required = schema.putArray("required");
        required.add("message");
        required.add("summary");
        required.add("actions");
        schema.put("additionalProperties", false);
        return schema;
    }

    private List<AssistantToolCall> extractAssistantToolCalls(JsonNode responseNode) {
        if (responseNode == null || !responseNode.path("output").isArray()) {
            return List.of();
        }
        List<AssistantToolCall> toolCalls = new java.util.ArrayList<>();
        for (JsonNode item : responseNode.path("output")) {
            if (!"function_call".equals(item.path("type").asText(""))) {
                continue;
            }
            String callId = item.path("call_id").asText(null);
            String name = item.path("name").asText(null);
            String arguments = item.path("arguments").asText(null);
            if (isBlank(callId) || isBlank(name)) {
                continue;
            }
            toolCalls.add(new AssistantToolCall(callId, name, arguments == null ? "{}" : arguments));
        }
        return toolCalls;
    }

    private int estimateAssistantInputChars(AssistantResponseRequest request) {
        int total = 0;
        if (!isBlank(request.instructions())) {
            total += request.instructions().length();
        }
        if (!isBlank(request.inputText())) {
            total += request.inputText().length();
        }
        if (request.toolOutputs() != null) {
            for (AssistantToolOutput toolOutput : request.toolOutputs()) {
                if (!isBlank(toolOutput.outputJson())) {
                    total += toolOutput.outputJson().length();
                }
            }
        }
        return total;
    }

    private int estimateTextResponseInputChars(OpenAiResponsesTextRequest request) {
        if (request == null) {
            return 0;
        }
        int total = 0;
        if (!isBlank(request.instructions())) {
            total += request.instructions().length();
        }
        if (!isBlank(request.input())) {
            total += request.input().length();
        }
        return total;
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

    private String extractOpenAiErrorParam(Throwable t) {
        String body = extractResponseBody(t);
        if (isBlank(body)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String param = node.path("error").path("param").asText("");
            return param == null ? "" : param.trim().toLowerCase();
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

    private AiProviderSelection.SelectedProvider resolveSelectedProvider(String explicitProvider) {
        AiProviderSelection.SelectedProvider selected = providerSelection.resolve(explicitProvider);
        String resolvedProvider = firstNonBlank(selected.provider(), activeProvider, "openai");
        String resolvedApiKey = firstNonBlank(selected.apiKey(), apiKey);
        String resolvedBaseUrl = normalizeProviderBaseUrl(firstNonBlank(selected.baseUrl(), baseUrl, "https://api.openai.com"));
        String resolvedModel = firstNonBlank(selected.model(), model, DEFAULT_MODEL);
        return new AiProviderSelection.SelectedProvider(
                resolvedProvider,
                resolvedApiKey,
                resolvedBaseUrl,
                resolvedModel,
                selected.imageModel()
        );
    }

    private AiProviderSelection.SelectedProvider resolveImageProvider(String explicitProvider) {
        AiProviderSelection.SelectedProvider selected = providerSelection.resolveOrNull(explicitProvider);
        AiProviderSelection.SelectedProvider resolvedSelected = normalizeResolvedProvider(selected, activeProvider);
        if (resolvedSelected != null && !isBlank(resolvedSelected.imageModel())) {
            return resolvedSelected;
        }

        AiProviderSelection.SelectedProvider fallback = normalizeResolvedProvider(
                providerSelection.resolveOrNull("openai"),
                "openai"
        );
        if (fallback != null && !isBlank(fallback.imageModel())) {
            if (resolvedSelected != null && !"openai".equalsIgnoreCase(resolvedSelected.provider())) {
                log.info("OpenAI image provider fallback enabled requestedProvider={} fallbackProvider=openai",
                        resolvedSelected.provider());
            }
            return fallback;
        }

        return resolvedSelected;
    }

    private AiProviderSelection.SelectedProvider normalizeResolvedProvider(
            AiProviderSelection.SelectedProvider selected,
            String fallbackProvider
    ) {
        if (selected == null) {
            return null;
        }
        String resolvedProvider = firstNonBlank(selected.provider(), fallbackProvider, activeProvider, "openai");
        String resolvedApiKey = firstNonBlank(selected.apiKey(), apiKey);
        String resolvedBaseUrl = normalizeProviderBaseUrl(firstNonBlank(selected.baseUrl(), baseUrl, "https://api.openai.com"));
        String resolvedModel = firstNonBlank(selected.model(), model, DEFAULT_MODEL);
        return new AiProviderSelection.SelectedProvider(
                resolvedProvider,
                resolvedApiKey,
                resolvedBaseUrl,
                resolvedModel,
                trimToNull(selected.imageModel())
        );
    }

    private ObjectNode buildImageRequestPayload(AiProviderSelection.SelectedProvider provider, String prompt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", provider.imageModel());
        payload.put("prompt", prompt);
        payload.put("size", "1024x1024");
        payload.put("n", 1);
        return payload;
    }

    private String generateImage(AiProviderSelection.SelectedProvider provider, String prompt, String traceId) {
        WebClient targetWebClient = getWebClient(provider);
        ObjectNode payload = buildImageRequestPayload(provider, prompt);

        int payloadBytes = logFinalPayload(traceId, "images", provider.imageModel(), payload, prompt.length(), 0);
        ImageResponsePayload responsePayload = targetWebClient.post()
                .uri("/v1/images/generations")
                .bodyValue(payload)
                .exchangeToMono(response -> readImageResponsePayload(response))
                .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                .timeout(Duration.ofMillis(effectiveOverallTimeoutMs))
                .block();

        if (responsePayload == null || responsePayload.body() == null || responsePayload.body().length == 0) {
            return null;
        }

        MediaType contentType = responsePayload.contentType();
        if (contentType != null && contentType.getType().equalsIgnoreCase("image")) {
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(responsePayload.body());
        }

        String responseBody = new String(responsePayload.body(), StandardCharsets.UTF_8);

        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree(responseBody);
        } catch (Exception parseError) {
            log.warn("OpenAI image response parse failed traceId={} provider={} model={} payloadBytes={} reason={} bodyPreview={}",
                    traceId,
                    provider.provider(),
                    provider.imageModel(),
                    payloadBytes,
                    safeMsg(parseError),
                    previewForLog(responseBody, 240));
            return null;
        }

        JsonNode dataNode = responseNode.path("data");
        if (!dataNode.isArray() || dataNode.isEmpty()) {
            log.warn("OpenAI image response missing data traceId={} provider={} model={} payloadBytes={}",
                    traceId, provider.provider(), provider.imageModel(), payloadBytes);
            return null;
        }

        JsonNode first = dataNode.get(0);
        String b64 = trimToNull(first.path("b64_json").asText(null));
        if (b64 != null) {
            return "data:image/png;base64," + b64;
        }
        return trimToNull(first.path("url").asText(null));
    }

    private reactor.core.publisher.Mono<ImageResponsePayload> readImageResponsePayload(ClientResponse response) {
        HttpStatusCode statusCode = response.statusCode();
        MediaType contentType = response.headers().contentType().orElse(null);
        return response.bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> {
                    if (statusCode.is2xxSuccessful()) {
                        return reactor.core.publisher.Mono.just(new ImageResponsePayload(contentType, body));
                    }
                    return reactor.core.publisher.Mono.error(WebClientResponseException.create(
                            statusCode.value(),
                            statusCode.toString(),
                            response.headers().asHttpHeaders(),
                            body,
                            StandardCharsets.UTF_8
                    ));
                });
    }

    private Double normalizeChatCompletionsTemperature(AiProviderSelection.SelectedProvider selectedProvider,
                                                       Double requestedTemperature) {
        if (selectedProvider == null) {
            return requestedTemperature;
        }
        if ("kimi".equalsIgnoreCase(selectedProvider.provider())
                && "kimi-k2.5".equalsIgnoreCase(firstNonBlank(selectedProvider.model()))) {
            return 1.0d;
        }
        return requestedTemperature;
    }

    private WebClient getWebClient(AiProviderSelection.SelectedProvider provider) {
        String cacheKey = provider.provider() + "|" + provider.baseUrl() + "|" + provider.apiKey();
        return providerWebClients.computeIfAbsent(cacheKey, ignored -> buildWebClient(provider.baseUrl(), provider.apiKey()));
    }

    private WebClient buildWebClient(String baseUrl, String apiKey) {
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(config.getMaxInMemorySize()))
                .build();
        return WebClient.builder()
                .baseUrl(normalizeProviderBaseUrl(baseUrl))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + firstNonBlank(apiKey, ""))
                .exchangeStrategies(exchangeStrategies)
                .clientConnector(clientConnector)
                .build();
    }

    private boolean supportsResponses(String provider) {
        return "openai".equalsIgnoreCase(firstNonBlank(provider, activeProvider));
    }

    private String normalizeProviderBaseUrl(String rawBaseUrl) {
        String base = firstNonBlank(rawBaseUrl, "https://api.openai.com");
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/v1")) {
            return base.substring(0, base.length() - 3);
        }
        return base;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        if (isVocabularyMarkdownPrompt(content)) {
            return safeVocabularyPromptSummary(content);
        }
        String redacted = redactSourceContext(content);
        redacted = redacted.replaceAll("sk-[a-zA-Z0-9]+", "sk-***");
        redacted = redacted.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        return redacted;
    }

    private String redactSourceContext(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed == null || (!parsed.isObject() && !parsed.isArray())) {
                return content;
            }
            JsonNode copy = parsed.deepCopy();
            if (!redactSourceContextFields(copy)) {
                return content;
            }
            return objectMapper.writeValueAsString(copy);
        } catch (JsonProcessingException exception) {
            return containsSensitivePromptMarker(content)
                    ? safeUnparseablePromptSummary(content)
                    : content;
        }
    }

    private boolean redactSourceContextFields(JsonNode node) {
        boolean redacted = false;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> fieldNames = new java.util.ArrayList<>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode value = object.get(fieldName);
                if (isSourceContextField(fieldName)) {
                    object.put(fieldName, "[REDACTED]");
                    redacted = true;
                } else if (value != null && value.isTextual()
                        && containsSensitivePromptMarker(value.asText())) {
                    object.put(fieldName, safeSensitivePromptSummary(value.asText()));
                    redacted = true;
                } else if (value != null) {
                    redacted |= redactSourceContextFields(value);
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode item = array.get(i);
                if (item != null && item.isTextual()
                        && containsSensitivePromptMarker(item.asText())) {
                    array.set(i, objectMapper.getNodeFactory().textNode(
                            safeSensitivePromptSummary(item.asText())));
                    redacted = true;
                } else if (item != null) {
                    redacted |= redactSourceContextFields(item);
                }
            }
        }
        return redacted;
    }

    private boolean isVocabularyMarkdownPrompt(String content) {
        return content != null
                && content.contains(VOCABULARY_MARKDOWN_PROMPT_PREFIX)
                && content.contains(VOCABULARY_THEME_PURPOSE_TAG);
    }

    private boolean containsSensitivePromptMarker(String content) {
        if (content == null) {
            return false;
        }
        String normalized = content.toLowerCase(java.util.Locale.ROOT);
        return content.contains(VOCABULARY_MARKDOWN_PROMPT_PREFIX)
                || content.contains(VOCABULARY_THEME_PURPOSE_TAG)
                || normalized.contains("\"sourcecontext\"")
                || normalized.contains("\"capturedsourcecontext\"")
                || normalized.contains("\"contexttext\"");
    }

    private String safeVocabularyPromptSummary(String content) {
        return "[REDACTED_VOCABULARY_PROMPT chars=" + content.length()
                + " sha256=" + sha256Hex(content) + "]";
    }

    private String safeSensitivePromptSummary(String content) {
        return isVocabularyMarkdownPrompt(content)
                ? safeVocabularyPromptSummary(content)
                : safeUnparseablePromptSummary(content);
    }

    private String safeUnparseablePromptSummary(String content) {
        return "[REDACTED_UNPARSEABLE_PROMPT chars=" + content.length()
                + " sha256=" + sha256Hex(content) + "]";
    }

    private boolean isSourceContextField(String fieldName) {
        return "capturedSourceContext".equalsIgnoreCase(fieldName)
                || "sourceContext".equalsIgnoreCase(fieldName)
                || "contextText".equalsIgnoreCase(fieldName);
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

    private void recordUsage(String provider,
                             String model,
                             String providerRequestId,
                             Integer inputTokens,
                             Integer cachedInputTokens,
                             Integer outputTokens,
                             Integer reasoningTokens,
                             Integer totalTokens) {
        if (aiUsageRecorder == null || (inputTokens == null && outputTokens == null && reasoningTokens == null && totalTokens == null)) {
            return;
        }
        aiUsageRecorder.recordCurrentContext(
                provider,
                model,
                providerRequestId,
                inputTokens,
                cachedInputTokens,
                outputTokens,
                reasoningTokens,
                totalTokens
        );
    }

    private static Integer responseInputTokens(ChatResponse response) {
        return response == null || response.getUsage() == null ? null : firstNonNull(response.getUsage().getPromptTokens(), response.getUsage().getInputTokens());
    }

    private static Integer responseCachedInputTokens(ChatResponse response) {
        return response == null || response.getUsage() == null || response.getUsage().getPromptTokensDetails() == null
                ? null : response.getUsage().getPromptTokensDetails().getCachedTokens();
    }

    private static Integer responseOutputTokens(ChatResponse response) {
        return response == null || response.getUsage() == null ? null : firstNonNull(response.getUsage().getCompletionTokens(), response.getUsage().getOutputTokens());
    }

    private static Integer responseReasoningTokens(ChatResponse response) {
        return response == null || response.getUsage() == null || response.getUsage().getCompletionTokensDetails() == null
                ? null : response.getUsage().getCompletionTokensDetails().getReasoningTokens();
    }

    private static Integer responseTotalTokens(ChatResponse response) {
        if (response == null || response.getUsage() == null) {
            return null;
        }
        Integer total = response.getUsage().getTotalTokens();
        return total != null ? total : sumTokens(responseInputTokens(response), responseOutputTokens(response), responseReasoningTokens(response));
    }

    private static Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static Integer sumTokens(Integer inputTokens, Integer outputTokens, Integer reasoningTokens) {
        if (inputTokens == null && outputTokens == null && reasoningTokens == null) {
            return null;
        }
        return defaultInt(inputTokens) + defaultInt(outputTokens) + defaultInt(reasoningTokens);
    }

    private static Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private static int defaultInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private boolean isConnectTimeout(Throwable t) {
        if (t == null) return false;
        String cls = t.getClass().getName();
        return cls.contains("ConnectTimeoutException") || cls.contains("ReadTimeoutException");
    }

    private record OpenAiCallResult(String content, int payloadBytes, boolean parseSuccess) {
    }

    private record StructuredOutputConfig(
            String schemaName,
            JsonNode schema,
            Double temperature,
            Integer maxTokens) {
    }

    private record ImageResponsePayload(MediaType contentType, byte[] body) {
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
        private String id;
        private List<Choice> choices;
        private Usage usage;

        @JsonProperty("id")
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        @JsonProperty("choices")
        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }

        @JsonProperty("usage")
        public Usage getUsage() { return usage; }
        public void setUsage(Usage usage) { this.usage = usage; }
    }

    private static class Choice {
        private Message message;

        @JsonProperty("message")
        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }

    private static class Usage {
        private Integer promptTokens;
        private Integer inputTokens;
        private Integer completionTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private TokenDetails promptTokensDetails;
        private TokenDetails completionTokensDetails;

        @JsonProperty("prompt_tokens")
        public Integer getPromptTokens() { return promptTokens; }
        public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

        @JsonProperty("input_tokens")
        public Integer getInputTokens() { return inputTokens; }
        public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }

        @JsonProperty("completion_tokens")
        public Integer getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

        @JsonProperty("output_tokens")
        public Integer getOutputTokens() { return outputTokens; }
        public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }

        @JsonProperty("total_tokens")
        public Integer getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

        @JsonProperty("prompt_tokens_details")
        public TokenDetails getPromptTokensDetails() { return promptTokensDetails; }
        public void setPromptTokensDetails(TokenDetails promptTokensDetails) { this.promptTokensDetails = promptTokensDetails; }

        @JsonProperty("completion_tokens_details")
        public TokenDetails getCompletionTokensDetails() { return completionTokensDetails; }
        public void setCompletionTokensDetails(TokenDetails completionTokensDetails) { this.completionTokensDetails = completionTokensDetails; }
    }

    private static class TokenDetails {
        private Integer cachedTokens;
        private Integer reasoningTokens;

        @JsonProperty("cached_tokens")
        public Integer getCachedTokens() { return cachedTokens; }
        public void setCachedTokens(Integer cachedTokens) { this.cachedTokens = cachedTokens; }

        @JsonProperty("reasoning_tokens")
        public Integer getReasoningTokens() { return reasoningTokens; }
        public void setReasoningTokens(Integer reasoningTokens) { this.reasoningTokens = reasoningTokens; }
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

        public JsonNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", role);
            ArrayNode contentNode = node.putArray("content");
            if (content != null) {
                for (ResponseContentItem item : content) {
                    contentNode.add(item.toJson(objectMapper));
                }
            }
            return node;
        }
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

        public JsonNode toJson(ObjectMapper objectMapper) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            node.put("text", text);
            return node;
        }
    }
}



