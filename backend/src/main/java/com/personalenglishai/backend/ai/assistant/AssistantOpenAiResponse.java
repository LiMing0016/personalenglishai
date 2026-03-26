package com.personalenglishai.backend.ai.assistant;

import java.util.List;

public record AssistantOpenAiResponse(
        String responseId,
        String outputText,
        List<AssistantToolCall> toolCalls
) {
}
