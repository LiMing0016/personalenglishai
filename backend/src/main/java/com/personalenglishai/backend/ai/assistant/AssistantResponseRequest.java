package com.personalenglishai.backend.ai.assistant;

import java.util.List;

public record AssistantResponseRequest(
        String model,
        String instructions,
        String inputText,
        String previousResponseId,
        List<AssistantToolDefinition> tools,
        List<AssistantToolOutput> toolOutputs,
        boolean store
) {
}
