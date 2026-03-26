package com.personalenglishai.backend.service.writing.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScoreStagePromptPolicyRegistry")
class ScoreStagePromptPolicyRegistryTest {

    private final ScoreStagePromptPolicyRegistry registry =
            new ScoreStagePromptPolicyRegistry(List.of(new PostgradExamScoreStagePromptPolicy()));

    @Test
    @DisplayName("postgrad exam 应命中专项策略")
    void shouldResolvePostgradExamPolicy() {
        assertThat(registry.buildSystemPromptAddendum("postgrad", "exam", "task2"))
                .contains("当前学段为 postgrad exam");
        assertThat(registry.buildStageRules("postgrad", "exam", "task2"))
                .contains("[POSTGRAD_STAGE_RULES]")
                .contains("描述材料、解读含义、给出评论");
        assertThat(registry.buildTaskAnchors("postgrad", "exam", "task2"))
                .contains("考研 task2 判分锚点");
        assertThat(registry.resolveFewShotExample("postgrad", "exam", "task2", "default"))
                .contains("postgrad exam task2 材料作文");
    }

    @Test
    @DisplayName("未命中专项策略时应回退到通用默认")
    void shouldFallbackToDefaultWhenNoStagePolicyMatches() {
        assertThat(registry.buildSystemPromptAddendum("highschool", "exam", "task2")).isBlank();
        assertThat(registry.buildStageRules("highschool", "exam", "task2")).isBlank();
        assertThat(registry.buildTaskAnchors("highschool", "exam", "task2")).isBlank();
        assertThat(registry.resolveFewShotExample("highschool", "exam", "task2", "default-few-shot"))
                .isEqualTo("default-few-shot");
    }
}
