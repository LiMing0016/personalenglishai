package com.personalenglishai.backend.ai.englishassistant;

public record EnglishAssistantRouterResult(
        String scope,
        String taskType,
        boolean needsDraftContext,
        String refusalReason
) {
}
