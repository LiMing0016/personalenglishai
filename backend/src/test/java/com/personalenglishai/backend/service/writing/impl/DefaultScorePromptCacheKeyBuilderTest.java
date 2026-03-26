package com.personalenglishai.backend.service.writing.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultScorePromptCacheKeyBuilderTest {

    @Test
    @DisplayName("相同评分上下文应生成稳定 prompt cache key")
    void shouldBuildStablePromptCacheKey() {
        DefaultScorePromptCacheKeyBuilder builder = new DefaultScorePromptCacheKeyBuilder();

        ScorePromptContext context = new ScorePromptContext(
                "doc_123",
                "gpt-4o",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-prompt-hash",
                "rubric-hash",
                "task prompt",
                "topic title",
                160,
                200,
                25
        );

        assertThat(builder.build(context))
                .isEqualTo("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
    }
}
