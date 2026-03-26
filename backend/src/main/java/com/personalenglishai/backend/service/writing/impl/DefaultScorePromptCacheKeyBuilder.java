package com.personalenglishai.backend.service.writing.impl;

import org.springframework.stereotype.Component;

@Component
public class DefaultScorePromptCacheKeyBuilder {

    public String build(ScorePromptContext context) {
        if (context == null) {
            return "score:unknown";
        }
        return String.join(":",
                "score",
                normalize(context.model()),
                normalize(context.promptVersion()),
                normalize(context.rubricKey()),
                normalize(context.studyStage()),
                normalize(context.mode()),
                normalize(context.taskType())
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isEmpty() ? "unknown" : normalized;
    }
}
