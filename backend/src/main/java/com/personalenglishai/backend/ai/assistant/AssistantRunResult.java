package com.personalenglishai.backend.ai.assistant;

import java.util.List;

public record AssistantRunResult(
        String responseId,
        String message,
        List<String> summary,
        List<AssistantAction> actions,
        List<AssistantToolRun> toolRuns
) {
}
