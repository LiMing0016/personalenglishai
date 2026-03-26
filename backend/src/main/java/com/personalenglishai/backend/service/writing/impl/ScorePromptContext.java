package com.personalenglishai.backend.service.writing.impl;

public record ScorePromptContext(
        String docId,
        String model,
        String promptVersion,
        String rubricKey,
        String studyStage,
        String mode,
        String taskType,
        String taskPromptHash,
        String renderedRubricHash,
        String taskPrompt,
        String topicTitle,
        Integer minWords,
        Integer recommendedMaxWords,
        Integer maxScore
) {
}
