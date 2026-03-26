package com.personalenglishai.backend.ai.assistant;

import java.util.List;

public record AssistantStructuredResponse(
        String message,
        List<String> summary,
        List<AssistantAction> actions
) {
}
