package com.personalenglishai.backend.ai.assistant;

public record AssistantToolCall(
        String callId,
        String name,
        String argumentsJson
) {
}
