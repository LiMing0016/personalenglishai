package com.personalenglishai.backend.ai.assistant;

import java.util.List;

public record AssistantEvent(
        String type,
        String status,
        List<AssistantToolRun> toolRuns,
        AssistantRunResult finalResult,
        String message
) {
}
