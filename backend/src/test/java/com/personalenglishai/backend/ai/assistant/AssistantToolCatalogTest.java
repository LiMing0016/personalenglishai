package com.personalenglishai.backend.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantToolCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void strictToolSchemasShouldRequireEveryDeclaredProperty() throws Exception {
        for (AssistantToolDefinition tool : AssistantToolCatalog.defaultTools()) {
            JsonNode schema = objectMapper.readTree(tool.parametersJson());
            Set<String> propertyNames = new HashSet<>();
            Iterator<String> names = schema.path("properties").fieldNames();
            while (names.hasNext()) {
                propertyNames.add(names.next());
            }

            Set<String> requiredNames = new HashSet<>();
            for (JsonNode required : schema.path("required")) {
                requiredNames.add(required.asText());
            }

            assertThat(requiredNames)
                    .as("tool %s should require every declared property when strict mode is enabled", tool.name())
                    .containsExactlyInAnyOrderElementsOf(propertyNames);
        }
    }
}
