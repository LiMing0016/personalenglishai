package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.PolishEssayRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingPolishServiceImplTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private RubricService rubricService;

    @Mock
    private RubricTextBuilder rubricTextBuilder;

    @Mock
    private WritingEvaluateService writingEvaluateService;

    @Test
    void polishEssayShouldSendTopicContentAndUseKeepFrameworkRoute() {
        WritingPolishServiceImpl service = createService();
        String essayText = ("This sentence explains the table trend and keeps the structure stable. ".repeat(20)).trim();
        String polishedEssay = ("The table shows that household ownership of durable goods kept rising from 2014 to 2023, with air conditioners growing the fastest. ").repeat(14).trim();

        when(rubricService.normalizeStage(anyString())).thenReturn("postgrad");
        when(rubricService.normalizeMode(anyString())).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(mockRubric());
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("rubric");

        WritingEvaluateResponse baseline = mockEvaluation(
                52,
                "Band 2",
                "partially_off_topic",
                "mostly_completed",
                "partial_key_points",
                Map.of(
                        "task_achievement", "C",
                        "content_quality", "C",
                        "structure", "B",
                        "grammar", "C",
                        "expression", "C"
                )
        );
        WritingEvaluateResponse candidate = mockEvaluation(
                66,
                "Band 3",
                "mostly_on_topic",
                "mostly_completed",
                "most_key_points",
                Map.of(
                        "task_achievement", "B",
                        "content_quality", "B",
                        "structure", "B",
                        "grammar", "C",
                        "expression", "C"
                )
        );
        when(writingEvaluateService.evaluateForPolish(any())).thenReturn(baseline, candidate);
        when(openAiClient.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"summary\":{\"strengths\":[\"结构较稳\"],\"improvements\":[\"补足题意\"]},\"topicAlignmentStatus\":\"partial\",\"polishedEssay\":\""
                        + polishedEssay.replace("\"", "\\\"")
                        + "\"}");

        PolishEssayRequest request = new PolishEssayRequest();
        request.setText(essayText);
        request.setTier("perfect");
        request.setStudyStage("postgrad");
        request.setWritingMode("exam");
        request.setTaskType("task2");
        request.setTopicContent("表格显示2014至2023年主要耐用消费品拥有量变化。");
        request.setTaskPrompt("describe the table briefly, explain its intended meaning, and give your comments.");
        request.setMinWords(160);
        request.setRecommendedMaxWords(200);

        var response = service.polishEssay(request);

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).callWithTraceId(
                systemPromptCaptor.capture(),
                userPromptCaptor.capture(),
                anyString(),
                anyDouble(),
                anyInt()
        );

        assertThat(systemPromptCaptor.getValue())
                .contains("rubric_key=postgrad-exam-v1")
                .contains("polish_rubric_key=postgrad.exam.task2.polish.v1")
                .contains("route=topic_correction_then_polish")
                .contains("target_band_rank=1")
                .contains("[WORD_RANGE_RULE]")
                .contains("200-229");

        assertThat(userPromptCaptor.getValue())
                .contains("题目内容：")
                .contains("表格显示2014至2023年主要耐用消费品拥有量变化")
                .contains("写作要求：")
                .contains("describe the table briefly")
                .contains("当前路由：topic_correction_then_polish")
                .doesNotContain("用户补充要求");

        assertThat(response.getRoute()).isEqualTo("topic_correction_then_polish");
        assertThat(response.getProcessingModeLabel()).isEqualTo("本次处理：先纠偏再润色");
        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getFallbackToOriginal()).isFalse();
        assertThat(response.getPolishedEssay()).contains("The table shows that household ownership");
        assertThat(response.getSourceBandRank()).isEqualTo(4);
        assertThat(response.getTargetBandRank()).isEqualTo(1);
        assertThat(response.getFinalBand()).isEqualTo("Band 3");
    }

    @Test
    void polishEssayShouldFallbackToOriginalWhenCandidateRegresses() {
        WritingPolishServiceImpl service = createService();

        when(rubricService.normalizeStage(anyString())).thenReturn("postgrad");
        when(rubricService.normalizeMode(anyString())).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(mockRubric());
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("rubric");

        WritingEvaluateResponse baseline = mockEvaluation(
                68,
                "Band 3",
                "mostly_on_topic",
                "mostly_completed",
                "most_key_points",
                Map.of(
                        "task_achievement", "B",
                        "content_quality", "B",
                        "structure", "B",
                        "grammar", "B",
                        "expression", "B"
                )
        );
        WritingEvaluateResponse candidate = mockEvaluation(
                60,
                "Band 2",
                "partially_off_topic",
                "partially_completed",
                "partial_key_points",
                Map.of(
                        "task_achievement", "C",
                        "content_quality", "B",
                        "structure", "B",
                        "grammar", "B",
                        "expression", "B"
                )
        );
        baseline.getExamPolicy().setCapScore(68);
        candidate.getExamPolicy().setCapScore(60);

        when(writingEvaluateService.evaluateForPolish(any())).thenReturn(baseline, candidate);
        when(openAiClient.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "summary":{"strengths":["表达较好"],"improvements":["任务完成下降"]},
                          "topicAlignmentStatus":"partial",
                          "polishedEssay":"This is a risky rewrite."
                        }
                        """);

        PolishEssayRequest request = new PolishEssayRequest();
        request.setText("This is my essay.");
        request.setTier("steady");
        request.setStudyStage("postgrad");
        request.setWritingMode("exam");
        request.setTaskType("task2");
        request.setTopicContent("图画展示家庭教育中的责任推诿。");
        request.setTaskPrompt("describe the drawing, interpret its meaning, and give your comments.");
        request.setMinWords(160);
        request.setRecommendedMaxWords(200);

        var response = service.polishEssay(request);

        assertThat(response.getAccepted()).isFalse();
        assertThat(response.getGuardTriggered()).isTrue();
        assertThat(response.getFallbackToOriginal()).isTrue();
        assertThat(response.getTargetMet()).isFalse();
        assertThat(response.getTargetGap()).contains("已回退到原文");
        assertThat(response.getBindingReason()).isEqualTo("topic_cap");
    }

    @Test
    void polishEssayShouldMapTargetBandByTier() {
        assertThat(runTierMapping("basic")).isEqualTo(3);
        assertThat(runTierMapping("steady")).isEqualTo(2);
        assertThat(runTierMapping("advanced")).isEqualTo(2);
        assertThat(runTierMapping("perfect")).isEqualTo(1);
    }

    private int runTierMapping(String tier) {
        WritingPolishServiceImpl service = createService();

        when(rubricService.normalizeStage(anyString())).thenReturn("postgrad");
        when(rubricService.normalizeMode(anyString())).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(mockRubric());
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("rubric");

        WritingEvaluateResponse baseline = mockEvaluation(
                48,
                "Band 2",
                "mostly_on_topic",
                "mostly_completed",
                "most_key_points",
                Map.of(
                        "task_achievement", "C",
                        "content_quality", "C",
                        "structure", "C",
                        "grammar", "C",
                        "expression", "C"
                )
        );
        WritingEvaluateResponse candidate = mockEvaluation(
                48,
                "Band 2",
                "mostly_on_topic",
                "mostly_completed",
                "most_key_points",
                Map.of(
                        "task_achievement", "C",
                        "content_quality", "C",
                        "structure", "C",
                        "grammar", "C",
                        "expression", "C"
                )
        );
        when(writingEvaluateService.evaluateForPolish(any())).thenReturn(baseline, candidate);
        when(openAiClient.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("""
                        {
                          "summary":{"strengths":["结构稳定"],"improvements":["继续提升内容"]},
                          "topicAlignmentStatus":"aligned",
                          "polishedEssay":"The rewritten essay keeps the same quality."
                        }
                        """);

        PolishEssayRequest request = new PolishEssayRequest();
        request.setText(("This draft is long enough for target-band mapping checks. ".repeat(20)).trim());
        request.setTier(tier);
        request.setStudyStage("postgrad");
        request.setWritingMode("exam");
        request.setTaskType("task2");
        request.setTopicContent("柱状图展示某市公园数量增长。");
        request.setTaskPrompt("describe the chart, interpret it, and comment on it.");
        request.setMinWords(160);
        request.setRecommendedMaxWords(200);

        return service.polishEssay(request).getTargetBandRank();
    }

    private WritingPolishServiceImpl createService() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new WritingPolishServiceImpl(
                openAiClient,
                rubricService,
                rubricTextBuilder,
                writingEvaluateService,
                new PolishRubricConfigService(objectMapper),
                objectMapper
        );
    }

    private RubricActiveResponse mockRubric() {
        RubricActiveResponse rubric = new RubricActiveResponse();
        rubric.setRubricKey("postgrad-exam-v1");
        rubric.setDimensions(List.of(
                dimension("task_achievement"),
                dimension("content_quality"),
                dimension("structure"),
                dimension("grammar"),
                dimension("expression")
        ));
        return rubric;
    }

    private RubricActiveResponse.DimensionDto dimension(String key) {
        RubricActiveResponse.DimensionDto dto = new RubricActiveResponse.DimensionDto();
        dto.setDimensionKey(key);
        dto.setLevels(List.of(
                level("A", 90),
                level("B", 80),
                level("C", 70),
                level("D", 60)
        ));
        return dto;
    }

    private RubricActiveResponse.LevelDto level(String name, int score) {
        RubricActiveResponse.LevelDto dto = new RubricActiveResponse.LevelDto();
        dto.setLevel(name);
        dto.setScore(score);
        dto.setCriteria(name);
        return dto;
    }

    private WritingEvaluateResponse mockEvaluation(int finalOverall,
                                                   String band,
                                                   String relevance,
                                                   String taskCompletion,
                                                   String coverage,
                                                   Map<String, String> grades) {
        WritingEvaluateResponse response = new WritingEvaluateResponse();
        response.setGrades(grades);
        response.setDimensionScores(Map.of(
                "task_achievement", finalOverall,
                "content_quality", finalOverall,
                "structure", finalOverall
        ));

        WritingEvaluateResponse.ScoreDto score = new WritingEvaluateResponse.ScoreDto();
        score.setOverall(finalOverall);
        response.setScore(score);

        WritingEvaluateResponse.DirectionAssessmentDto direction =
                new WritingEvaluateResponse.DirectionAssessmentDto(relevance, taskCompletion, coverage, band);
        WritingEvaluateResponse.ExamPolicyDto policy = new WritingEvaluateResponse.ExamPolicyDto();
        policy.setPolicyKey("postgrad-exam-policy-v1");
        policy.setRawOverall(finalOverall);
        policy.setFinalOverall(finalOverall);
        policy.setCapScore(finalOverall);
        policy.setDirectionAssessment(direction);
        response.setExamPolicy(policy);
        return response;
    }
}
