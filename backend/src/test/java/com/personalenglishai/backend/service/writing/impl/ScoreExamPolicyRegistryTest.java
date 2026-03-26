package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScoreExamPolicyRegistry")
class ScoreExamPolicyRegistryTest {

    private final ScoreExamPolicyRegistry registry =
            new ScoreExamPolicyRegistry(List.of(new PostgradExamScoreExamPolicy()));

    @Test
    @DisplayName("postgrad exam 应命中专项 policy")
    void shouldResolvePostgradExamPolicy() {
        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setEssay("word ".repeat(83).trim());
        request.setMinWords(100);

        WritingExamPolicyService.ExamPolicyResult result = registry.evaluate(
                "postgrad",
                "exam",
                "task1",
                request,
                Map.of(
                        "task_achievement", 95,
                        "content_quality", 90,
                        "structure", 85,
                        "vocabulary", 80,
                        "grammar", 85,
                        "expression", 90
                ),
                List.<WritingEvaluateResponse.ErrorDto>of()
        );

        assertThat(result.policyKey()).isEqualTo("postgrad-exam-policy-v1");
        assertThat(result.capScore()).isEqualTo(84);
        assertThat(result.deductionTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("未命中专项 policy 时应回退为原始总分")
    void shouldFallbackToRawOverallWhenNoPolicyMatches() {
        WritingExamPolicyService.ExamPolicyResult result = registry.evaluate(
                "highschool",
                "exam",
                "task2",
                new WritingEvaluateRequest(),
                Map.of(
                        "content_quality", 80,
                        "structure", 70,
                        "vocabulary", 75,
                        "grammar", 85,
                        "expression", 90
                ),
                List.of()
        );

        assertThat(result.policyKey()).isNull();
        assertThat(result.rawOverall()).isEqualTo(80);
        assertThat(result.finalOverall()).isEqualTo(80);
        assertThat(result.capScore()).isNull();
    }
}
