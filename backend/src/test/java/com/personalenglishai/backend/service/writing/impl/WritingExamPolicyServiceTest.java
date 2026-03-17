package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WritingExamPolicyService — postgrad exam policy")
class WritingExamPolicyServiceTest {

    private final WritingExamPolicyService service = new WritingExamPolicyService();

    @Test
    @DisplayName("task1 字数轻微不足时应封顶到 Band 4 并扣 3 分")
    void task1LightWordShortageShouldCapAndDeduct() {
        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setEssay("word ".repeat(83).trim());
        request.setMinWords(100);

        WritingExamPolicyService.ExamPolicyResult result = service.evaluate(
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
                List.of()
        );

        assertThat(result.policyKey()).isEqualTo("postgrad-exam-policy-v1");
        assertThat(result.rawOverall()).isEqualTo(90);
        assertThat(result.capScore()).isEqualTo(84);
        assertThat(result.deductionTotal()).isEqualTo(3);
        assertThat(result.finalOverall()).isEqualTo(81);
        assertThat(result.flags()).containsEntry("light_under_word_count", true);
    }

    @Test
    @DisplayName("高质量但严重偏题时最终分应被封顶")
    void severelyOffTopicEssayShouldBeCapped() {
        WritingExamPolicyService.ExamPolicyResult result = service.evaluate(
                "postgrad",
                "exam",
                "task2",
                new WritingEvaluateRequest(),
                Map.of(
                        "task_achievement", 50,
                        "content_quality", 95,
                        "structure", 95,
                        "vocabulary", 95,
                        "grammar", 95,
                        "expression", 95
                ),
                List.of()
        );

        assertThat(result.rawOverall()).isEqualTo(82);
        assertThat(result.capScore()).isEqualTo(39);
        assertThat(result.finalOverall()).isEqualTo(39);
        assertThat(result.directionAssessment()).isNotNull();
        assertThat(result.directionAssessment().relevance()).isEqualTo("seriously_off_topic");
        assertThat(result.directionAssessment().maxBand()).isEqualTo("Band 1");
    }
}
