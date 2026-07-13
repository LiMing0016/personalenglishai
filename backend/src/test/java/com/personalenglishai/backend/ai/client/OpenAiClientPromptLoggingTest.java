package com.personalenglishai.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.personalenglishai.backend.ai.config.AiProviderProperties;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import com.personalenglishai.backend.service.vocabulary.ResolvedVocabularyTheme;
import com.personalenglishai.backend.service.vocabulary.VocabularyMarkdownPromptBuilder;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class OpenAiClientPromptLoggingTest {

    private static final String SYSTEM_PROMPT = "system prompt private value";
    private static final String USER_PROMPT = "user prompt private value";

    @Test
    void defaultDebugLoggingContainsOnlyPromptMetadata() throws Exception {
        OpenAiClient client = client(false);

        List<String> messages = capturePromptLogs(client, SYSTEM_PROMPT, USER_PROMPT);
        String output = String.join("\n", messages);

        assertThat(output)
                .contains("messagesCount=2")
                .contains("systemPromptLength=")
                .contains("userPromptLength=")
                .contains("systemPromptSha256=")
                .contains("userPromptSha256=")
                .doesNotContain(SYSTEM_PROMPT)
                .doesNotContain(USER_PROMPT)
                .doesNotContain("contentPreview")
                .doesNotContain("OpenAI system prompt (last)")
                .doesNotContain("OpenAI user prompt (last)")
                .doesNotContain("OpenAI messages payload");
    }

    @Test
    void explicitRawLoggingStillRedactsCapturedSourceContext() throws Exception {
        OpenAiClient client = client(true);
        String userPrompt = """
                {"term":"innovative","capturedSourceContext":"The private captured sentence."}
                """;

        String output = String.join("\n", capturePromptLogs(client, SYSTEM_PROMPT, userPrompt));

        assertThat(output)
                .contains(SYSTEM_PROMPT)
                .contains("innovative")
                .contains("[REDACTED]")
                .doesNotContain("The private captured sentence.");
    }

    @Test
    void vocabularyRawAndDebugPayloadLogsAreSafeWithoutChangingSentPrompt() throws Exception {
        String sourceContext = "SOURCE_CONTEXT_SENTINEL: private captured sentence";
        String themePurpose = "THEME_PURPOSE_SENTINEL: confidential learning goal";
        String sensitiveMaterial = "SENSITIVE_CORE_SENTINEL: private card material";
        ObjectMapper objectMapper = new ObjectMapper();
        VocabularyMarkdownPromptBuilder builder = new VocabularyMarkdownPromptBuilder(objectMapper);
        ObjectNode core = objectMapper.createObjectNode();
        core.put("term", "record");
        core.put("privateMaterial", sensitiveMaterial);
        ResolvedVocabularyTheme theme = new ResolvedVocabularyTheme(
                "theme-private", 4, "Private theme", themePurpose,
                "custom-markdown-v1", 1, "custom");
        String systemPrompt = builder.systemPrompt(theme);
        String userPrompt = builder.userPrompt(theme, core, sourceContext);

        AtomicReference<JsonNode> sentPayload = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            sentPayload.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = """
                    {"id":"chat-log-test","choices":[{"message":{"role":"assistant","content":"# Result"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            OpenAiClientConfig config = new OpenAiClientConfig();
            config.setMaxRetries(0);
            OpenAiClient client = client(
                    "http://127.0.0.1:" + server.getAddress().getPort(), true, true, config);

            String output = String.join("\n", captureLogs(
                    () -> client.callWithTraceId(systemPrompt, userPrompt, "trace-vocabulary-log")));

            assertThat(output)
                    .contains("OpenAI prompt raw")
                    .contains("FINAL OPENAI PAYLOAD")
                    .contains("[REDACTED_VOCABULARY_PROMPT")
                    .doesNotContain(sourceContext)
                    .doesNotContain(themePurpose)
                    .doesNotContain(sensitiveMaterial);
            assertThat(sentPayload.get()).isNotNull();
            assertThat(sentPayload.get().path("messages").get(0).path("content").asText())
                    .isEqualTo(systemPrompt);
            assertThat(sentPayload.get().path("messages").get(1).path("content").asText())
                    .isEqualTo(userPrompt);
        } finally {
            server.stop(0);
        }
    }

    private List<String> capturePromptLogs(
            OpenAiClient client,
            String systemPrompt,
            String userPrompt) throws Exception {
        return captureLogs(() -> invokePromptLogging(client, systemPrompt, userPrompt));
    }

    private List<String> captureLogs(ThrowingAction action) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiClient.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            action.run();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    private void invokePromptLogging(
            OpenAiClient client,
            String systemPrompt,
            String userPrompt) throws Exception {
        Class<?> messageType = Class.forName(OpenAiClient.class.getName() + "$Message");
        Constructor<?> messageConstructor = messageType.getDeclaredConstructor(String.class, String.class);
        messageConstructor.setAccessible(true);
        Object systemMessage = messageConstructor.newInstance("system", systemPrompt);
        Object userMessage = messageConstructor.newInstance("user", userPrompt);

        Class<?> requestType = Class.forName(OpenAiClient.class.getName() + "$ChatRequest");
        Constructor<?> requestConstructor = requestType.getDeclaredConstructor(String.class, List.class);
        requestConstructor.setAccessible(true);
        Object request = requestConstructor.newInstance("gpt-4o", List.of(systemMessage, userMessage));

        Method method = OpenAiClient.class.getDeclaredMethod(
                "logPromptPayload", String.class, requestType, String.class);
        method.setAccessible(true);
        method.invoke(client, "trace-logging", request, "chat_completions");
    }

    private OpenAiClient client(boolean rawLoggingEnabled) {
        return client("https://api.openai.com", false, rawLoggingEnabled, new OpenAiClientConfig());
    }

    private OpenAiClient client(
            String baseUrl,
            boolean debugLoggingEnabled,
            boolean rawLoggingEnabled,
            OpenAiClientConfig config) {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive("openai");
        AiProviderProperties.Provider provider = new AiProviderProperties.Provider();
        provider.setApiKey("test-key");
        provider.setBaseUrl(baseUrl);
        provider.setModel("gpt-4o");
        properties.getProviders().put("openai", provider);
        return new OpenAiClient(
                AiProviderSelection.from(properties),
                "test",
                "chat_completions",
                "gpt-4o",
                debugLoggingEnabled,
                rawLoggingEnabled,
                12000,
                config);
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
