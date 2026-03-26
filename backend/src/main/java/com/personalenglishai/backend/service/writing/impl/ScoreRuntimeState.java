package com.personalenglishai.backend.service.writing.impl;

public record ScoreRuntimeState(
        String model,
        String promptVersion,
        String rubricKey,
        String studyStage,
        String mode,
        String taskType,
        String taskPromptHash,
        String renderedRubricHash,
        String promptCacheKey,
        String lastScoreResponseId,
        String lastEssayHash
) {
}
