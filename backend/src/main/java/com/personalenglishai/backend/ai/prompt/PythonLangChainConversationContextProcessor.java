package com.personalenglishai.backend.ai.prompt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Message;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Options;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component("pythonLangChainConversationContextProcessor")
public class PythonLangChainConversationContextProcessor implements ConversationContextProcessor {

    private static final Logger log = LoggerFactory.getLogger(PythonLangChainConversationContextProcessor.class);

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String baseUrl;
    private final int timeoutMs;
    private final HttpClient httpClient;

    public PythonLangChainConversationContextProcessor(
            ObjectMapper objectMapper,
            @Value("${ai.context.conversation.python.enabled:false}") boolean enabled,
            @Value("${ai.context.conversation.python.base-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${ai.context.conversation.python.timeout-ms:800}") int timeoutMs
    ) {
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.max(100, timeoutMs)))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Result process(List<Message> rawMessages, Options options) {
        if (!enabled) {
            throw new IllegalStateException("python conversation processor is disabled");
        }
        String traceId = options == null ? null : options.traceId();
        long start = System.currentTimeMillis();
        int requestBodyBytes = 0;
        try {
            ProcessRequest req = new ProcessRequest(
                    options == null ? 8 : options.recentTurns(),
                    options == null ? null : options.conversationId(),
                    options == null ? null : options.traceId(),
                    toDtos(rawMessages)
            );
            String body = objectMapper.writeValueAsString(req);
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            requestBodyBytes = bodyBytes.length;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/context/conversation/process"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofMillis(Math.max(100, timeoutMs)))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("python context processor httpStatus=" + response.statusCode()
                        + " responseBody=" + safeBody(response.body()));
            }
            ProcessResponse parsed = objectMapper.readValue(response.body(), ProcessResponse.class);
            List<Message> messages = new ArrayList<>();
            if (parsed.messages != null) {
                for (MessageDto m : parsed.messages) {
                    if (m == null || isBlank(m.content)) continue;
                    messages.add(new Message(defaultIfBlank(m.role, "User"), m.content.trim()));
                }
            }
            log.info("[CTX_PROCESS] traceId={} processor=python ok=true latencyMs={} requestBodyBytes={} inputCount={} outputCount={}",
                    traceId, System.currentTimeMillis() - start, requestBodyBytes, rawMessages == null ? 0 : rawMessages.size(), messages.size());
            return new Result(List.copyOf(messages),
                    defaultIfBlank(parsed.processorName, "langchain-python"),
                    false,
                    parsed.summaryUsed);
        } catch (Exception e) {
            log.info("[CTX_PROCESS] traceId={} processor=python ok=false latencyMs={} requestBodyBytes={} error={}",
                    traceId, System.currentTimeMillis() - start, requestBodyBytes, safeError(e));
            throw new RuntimeException("python conversation context process failed", e);
        }
    }

    @Override
    public void appendMessages(String conversationId, List<Message> messages, String traceId) {
        if (!enabled || isBlank(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        int requestBodyBytes = 0;
        try {
            AppendRequest req = new AppendRequest(conversationId, traceId, toDtos(messages));
            String body = objectMapper.writeValueAsString(req);
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            requestBodyBytes = bodyBytes.length;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/context/conversation/append"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofMillis(Math.max(100, timeoutMs)))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("python context append httpStatus=" + response.statusCode()
                        + " responseBody=" + safeBody(response.body()));
            }
            log.info("[CTX_PROCESS] traceId={} processor=python action=append ok=true latencyMs={} requestBodyBytes={} messageCount={}",
                    traceId, System.currentTimeMillis() - start, requestBodyBytes, messages.size());
        } catch (Exception e) {
            log.info("[CTX_PROCESS] traceId={} processor=python action=append ok=false latencyMs={} requestBodyBytes={} error={}",
                    traceId, System.currentTimeMillis() - start, requestBodyBytes, safeError(e));
        }
    }

    private List<MessageDto> toDtos(List<Message> messages) {
        List<MessageDto> out = new ArrayList<>();
        if (messages == null) return out;
        for (Message m : messages) {
            if (m == null || isBlank(m.content())) continue;
            out.add(new MessageDto(defaultIfBlank(m.role(), "User"), m.content().trim()));
        }
        return out;
    }

    private String trimTrailingSlash(String url) {
        if (url == null) return "";
        String s = url.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private String defaultIfBlank(String v, String fallback) {
        return isBlank(v) ? fallback : v.trim();
    }

    private boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private String safeError(Throwable t) {
        if (t == null || t.getMessage() == null) return "";
        return t.getMessage().replace("\r", " ").replace("\n", " ");
    }

    private String safeBody(String body) {
        if (body == null) return "";
        String compact = body.replace("\r", " ").replace("\n", " ").trim();
        if (compact.length() > 500) {
            return compact.substring(0, 500) + "...";
        }
        return compact;
    }

    private static class MessageDto {
        public String role;
        public String content;

        public MessageDto() {
        }

        public MessageDto(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ProcessRequest {
        public Integer recentTurns;
        public String conversationId;
        public String traceId;
        public List<MessageDto> messages;

        public ProcessRequest() {
        }

        public ProcessRequest(Integer recentTurns, String conversationId, String traceId, List<MessageDto> messages) {
            this.recentTurns = recentTurns;
            this.conversationId = conversationId;
            this.traceId = traceId;
            this.messages = messages;
        }
    }

    private static class ProcessResponse {
        public List<MessageDto> messages;
        public String processorName;
        public boolean summaryUsed;
    }

    private static class AppendRequest {
        public String conversationId;
        public String traceId;
        public List<MessageDto> messages;

        public AppendRequest() {
        }

        public AppendRequest(String conversationId, String traceId, List<MessageDto> messages) {
            this.conversationId = conversationId;
            this.traceId = traceId;
            this.messages = messages;
        }
    }
}
