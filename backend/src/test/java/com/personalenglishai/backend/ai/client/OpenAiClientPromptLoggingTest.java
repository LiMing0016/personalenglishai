package com.personalenglishai.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.personalenglishai.backend.ai.config.AiProviderProperties;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
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

    private List<String> capturePromptLogs(
            OpenAiClient client,
            String systemPrompt,
            String userPrompt) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiClient.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            invokePromptLogging(client, systemPrompt, userPrompt);
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
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive("openai");
        AiProviderProperties.Provider provider = new AiProviderProperties.Provider();
        provider.setApiKey("test-key");
        provider.setBaseUrl("https://api.openai.com");
        provider.setModel("gpt-4o");
        properties.getProviders().put("openai", provider);
        return new OpenAiClient(
                AiProviderSelection.from(properties),
                "test",
                "chat_completions",
                "gpt-4o",
                false,
                rawLoggingEnabled,
                12000,
                new OpenAiClientConfig());
    }
}
