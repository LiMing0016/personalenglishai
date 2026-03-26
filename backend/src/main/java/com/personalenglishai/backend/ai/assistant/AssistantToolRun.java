package com.personalenglishai.backend.ai.assistant;

public record AssistantToolRun(
        String tool,
        String status,
        String summary
) {
}
