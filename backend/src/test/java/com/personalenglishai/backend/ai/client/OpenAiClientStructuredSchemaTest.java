package com.personalenglishai.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.config.AiProviderProperties;
import com.personalenglishai.backend.ai.config.AiProviderSelection;
import com.personalenglishai.backend.ai.config.OpenAiClientConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientStructuredSchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assistantStructuredOutputSchemaShouldRequireEveryActionProperty() throws Exception {
        OpenAiClient client = new OpenAiClient(
                providerSelection(),
                "test",
                "responses",
                "gpt-4o",
                false,
                false,
                12000,
                new OpenAiClientConfig()
        );

        java.lang.reflect.Method method = OpenAiClient.class.getDeclaredMethod("buildAssistantResponseSchema");
        method.setAccessible(true);
        JsonNode schema = (JsonNode) method.invoke(client);

        JsonNode actionItem = schema.path("properties").path("actions").path("items");
        Set<String> propertyNames = new HashSet<>();
        Iterator<String> names = actionItem.path("properties").fieldNames();
        while (names.hasNext()) {
            propertyNames.add(names.next());
        }

        Set<String> requiredNames = new HashSet<>();
        for (JsonNode required : actionItem.path("required")) {
            requiredNames.add(required.asText());
        }

        assertThat(requiredNames)
                .containsExactlyInAnyOrderElementsOf(propertyNames);
        assertThat(actionItem.path("properties").path("text").path("type").isArray()).isTrue();
        assertThat(actionItem.path("properties").path("panel").path("type").isArray()).isTrue();
    }

    private AiProviderSelection providerSelection() {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setActive("openai");

        AiProviderProperties.Provider openai = new AiProviderProperties.Provider();
        openai.setApiKey("test-key");
        openai.setBaseUrl("https://api.openai.com");
        openai.setModel("gpt-4o");
        properties.getProviders().put("openai", openai);
        return AiProviderSelection.from(properties);
    }
}
