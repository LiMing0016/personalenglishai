package com.personalenglishai.backend.ai.assistant;

public record AssistantToolDefinition(
        String name,
        String description,
        String parametersJson
) {
}
