package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.time.Duration;
import java.util.List;
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
    private static final String MODEL = "gpt-4o-mini";
    private static final Random RANDOM = new Random();

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

    public OpenAiClient(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${spring.profiles.active:}") String activeProfile,
            OpenAiClientConfig config) {
        this.apiKey = apiKey;
        this.config = config;
        this.activeProfile = activeProfile == null ? "" : activeProfile.toLowerCase();
        this.isDevOrLocal = isDevOrLocalProfile(activeProfile);
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

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null && !apiKey.isEmpty() ? apiKey : ""))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
    public String call(String systemPrompt, String userPrompt) {
        return callWithTraceId(systemPrompt, userPrompt, null, null);
    }

    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId) {
        return callWithTraceId(systemPrompt, userPrompt, traceId, null);
    }

    public String callWithTraceId(String systemPrompt, String userPrompt, String traceId, String xDebugFail) {
        long startTime = System.currentTimeMillis();
        int inputLength = (systemPrompt == null ? 0 : systemPrompt.length()) + (userPrompt == null ? 0 : userPrompt.length());
        AtomicInteger attemptCounter = new AtomicInteger(1);

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

            ChatRequest request = new ChatRequest(MODEL, List.of(
                    new Message("system", systemPrompt == null ? "" : systemPrompt),
                    new Message("user", userPrompt == null ? "" : userPrompt)
            ));
            logPromptPayload(traceId, request);

            ChatResponse response = webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .timeout(Duration.ofMillis(config.getResponseTimeoutMs()))
                    .retryWhen(retrySpec)
                    .timeout(Duration.ofMillis(config.getOverallTimeoutMs()))
                    .block();

            long latency = System.currentTimeMillis() - startTime;
            if (response == null || response.getChoices() == null || response.getChoices().isEmpty() ||
                    response.getChoices().get(0).getMessage() == null) {
                throw new RuntimeException("Empty response from OpenAI API");
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("Empty content in OpenAI response");
            }

            circuitBreaker.recordSuccess();
            log.info("OpenAI call succeeded traceId={} attempt={} latencyMs={} httpStatus=200 inputLength={} outputLength={}",
                    traceId, attemptCounter.get(), latency, inputLength, content.length());
            return content;

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            Throwable root = rootCause(e);
            String errorType = classifyError(e);
            String httpStatus = extractHttpStatus(e);
            String rootCauseClass = root != null ? root.getClass().getSimpleName() : "";
            String rootCauseMsg = root != null ? safeMsg(root) : "";
            String openaiRequestId = extractOpenAiRequestId(e);

            circuitBreaker.recordFailure();
            log.error("OpenAI call failed traceId={} attempt={} latencyMs={} errorType={} httpStatus={} rootCauseClass={} rootCauseMsg={} inputLength={}{}",
                    traceId, attemptCounter.get(), latency, errorType, httpStatus != null ? httpStatus : "",
                    rootCauseClass, rootCauseMsg, inputLength,
                    openaiRequestId != null ? " openaiRequestId=" + openaiRequestId : "");

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

    private void logPromptPayload(String traceId, ChatRequest request) {
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

        log.info("OpenAI prompt traceId={} model={} messagesCount={} systemCount={} userCount={} systemPromptLength={} userPromptLength={}",
                traceId, request.getModel(), messages.size(), systemCount, userCount, lastSystem.length(), lastUser.length());
        if (systemCount > 1 || userCount > 1) {
            log.warn("OpenAI prompt traceId={} multiple system/user detected systemCount={} userCount={}",
                    traceId, systemCount, userCount);
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

    // Request/Response DTOs
    private static class ChatRequest {
        private String model;
        private List<Message> messages;
        private Double temperature;

        public ChatRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
            this.temperature = 0.2d;
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
}



