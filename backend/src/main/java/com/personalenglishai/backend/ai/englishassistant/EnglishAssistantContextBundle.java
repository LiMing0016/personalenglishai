package com.personalenglishai.backend.ai.englishassistant;

public record EnglishAssistantContextBundle(
        String assignmentText,
        String selectedText,
        String draftExcerpt,
        String assistantOutputExcerpt,
        String rubricSummary,
        String recentTurnsText,
        String summaryText,
        String trimmedContextMode,
        boolean softLimitExceeded,
        boolean hardLimitExceeded,
        Integer inputTokens
) {
}
