package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.TrustedRewriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WritingEvaluateMockService — 考研 Prompt 与 Rubric 版本")
class WritingEvaluateMockServiceTest {

    @Mock private RubricService rubricService;
    @Mock private RubricTextBuilder rubricTextBuilder;
    @Mock private OpenAiClient openAiClient;
    @Mock private UserAbilityProfileMapper abilityProfileMapper;
    @Mock private WritingEvaluationPersistenceService writingEvaluationPersistenceService;
    @Mock private WritingExamPolicyService writingExamPolicyService;
    @Mock private GrammarCheckService grammarCheckService;
    @Mock private TrustedRewriteService trustedRewriteService;

    private WritingEvaluateMockService service;

    @BeforeEach
    void setUp() {
        service = new WritingEvaluateMockService(
                rubricService,
                rubricTextBuilder,
                openAiClient,
                new ObjectMapper(),
                abilityProfileMapper,
                writingEvaluationPersistenceService,
                writingExamPolicyService,
                grammarCheckService,
                trustedRewriteService
        );
    }

    @Test
    @DisplayName("postgrad task1 Prompt 应包含功能写作规则")
    void postgradTask1PromptContainsFunctionalWritingRules() {
        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setEssay("Dear Sir, I am writing to complain about the broken heater in my dormitory.");
        request.setTaskPrompt("Write a complaint letter to the dormitory manager.");
        request.setTaskType("task1");

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "buildUserPrompt",
                request,
                "rubric text",
                "exam",
                "postgrad",
                "postgrad-exam-v1",
                "task1"
        );

        assertThat(prompt)
                .contains("study_stage=postgrad")
                .contains("rubric_key=postgrad-exam-v1")
                .contains("功能写作")
                .contains("收信对象和身份关系")
                .contains("不要按泛议论文标准打分");
    }

    @Test
    @DisplayName("postgrad task2 Prompt 应包含材料作文规则")
    void postgradTask2PromptContainsMaterialWritingRules() {
        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setEssay("The picture shows two young people planting a tree together.");
        request.setTaskPrompt("Describe the picture, explain its meaning, and give your comment.");
        request.setTaskType("task2");

        String prompt = ReflectionTestUtils.invokeMethod(
                service,
                "buildUserPrompt",
                request,
                "rubric text",
                "exam",
                "postgrad",
                "postgrad-exam-v1",
                "task2"
        );

        assertThat(prompt)
                .contains("study_stage=postgrad")
                .contains("材料作文")
                .contains("描述材料、解读含义、给出评论")
                .contains("不要只给空泛评论而忽略材料描述");
    }

    @Test
    @DisplayName("postgrad 不应强制改为 exam 模式")
    void postgradModeShouldRespectRequestMode() {
        when(rubricService.normalizeMode("free")).thenReturn("free");

        String mode = ReflectionTestUtils.invokeMethod(service, "resolveModeForRequest", "postgrad", "free");

        assertThat(mode).isEqualTo("free");
    }

    @Test
    @DisplayName("评分链路应默认复用 Lite 语法检查口径")
    void evaluateShouldReuseLiteGrammarCheck() {
        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setUserId(1L);
        request.setStudyStage("postgrad");
        request.setMode("exam");
        request.setTaskType("task2");
        request.setDocumentId("doc-1");
        request.setEssay("The picture shows an old father in the middle.");

        RubricActiveResponse rubric = buildRubric("postgrad-exam-v1", "exam",
                "task_achievement", "content_quality", "structure", "vocabulary", "grammar", "expression");

        when(rubricService.normalizeStage("postgrad")).thenReturn("postgrad");
        when(rubricService.normalizeMode("exam")).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(rubric);
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("rubric text");
        when(openAiClient.callWithTraceId(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn(aiResultJson());
        when(grammarCheckService.check(request.getEssay(), "lite")).thenReturn(List.of());
        when(trustedRewriteService.filterTrustedTrinkaSuggestions(
                eq(1L),
                anyString(),
                anyString(),
                anyList()
        )).thenAnswer(invocation -> invocation.getArgument(3));
        when(writingExamPolicyService.evaluate(
                eq("postgrad"),
                eq("exam"),
                eq("task2"),
                same(request),
                anyMap(),
                anyList()
        )).thenReturn(new WritingExamPolicyService.ExamPolicyResult(
                "postgrad-exam-policy-v1", 75, 75, null, 0, Map.of(), List.of(), null
        ));
        when(abilityProfileMapper.selectByUserId(1L)).thenReturn(null);
        when(openAiClient.getModel()).thenReturn("gpt-test");

        WritingEvaluateResponse response = service.evaluate(request);

        verify(grammarCheckService).check(request.getEssay(), "lite");
        assertThat(response.getErrors()).isEmpty();
        assertThat(response.getScore().getOverall()).isEqualTo(75);
        assertThat(response.getRawErrorCount()).isZero();
        assertThat(response.getDisplayErrorCount()).isZero();
    }

    @Test
    @DisplayName("能力画像应记录当前生效的 rubric key")
    void updateAbilityProfileUsesEffectiveRubricKey() {
        when(abilityProfileMapper.selectByUserId(1L)).thenReturn(null);
        when(openAiClient.getModel()).thenReturn("gpt-test");

        ReflectionTestUtils.invokeMethod(
                service,
                "updateAbilityProfile",
                1L,
                Map.of(
                        "grammar", 80,
                        "vocabulary", 78,
                        "structure", 75,
                        "content_quality", 82,
                        "expression", 76,
                        "task_achievement", 84
                ),
                "postgrad-exam-v1"
        );

        ArgumentCaptor<UserAbilityProfile> captor = ArgumentCaptor.forClass(UserAbilityProfile.class);
        verify(abilityProfileMapper).upsertAbilityScores(captor.capture());
        assertThat(captor.getValue().getRubricVersion()).isEqualTo("postgrad-exam-v1");
    }

    private RubricActiveResponse buildRubric(String rubricKey, String mode, String... dimensionKeys) {
        RubricActiveResponse rubric = new RubricActiveResponse();
        rubric.setRubricKey(rubricKey);
        rubric.setMode(mode);
        for (String dimensionKey : dimensionKeys) {
            RubricActiveResponse.DimensionDto dimension = new RubricActiveResponse.DimensionDto();
            dimension.setDimensionKey(dimensionKey);
            dimension.setDisplayName(dimensionKey);
            dimension.setLevels(List.of(
                    level("A", 90),
                    level("B", 75),
                    level("C", 60),
                    level("D", 42),
                    level("E", 20)
            ));
            rubric.getDimensions().add(dimension);
        }
        return rubric;
    }

    private RubricActiveResponse.LevelDto level(String level, int score) {
        RubricActiveResponse.LevelDto dto = new RubricActiveResponse.LevelDto();
        dto.setLevel(level);
        dto.setScore(score);
        dto.setCriteria(level + " criteria");
        return dto;
    }

    private String aiResultJson() {
        return """
                {
                  "mode": "exam",
                  "grades": {
                    "task_achievement": "B",
                    "content_quality": "B",
                    "structure": "B",
                    "vocabulary": "B",
                    "grammar": "B",
                    "expression": "B"
                  },
                  "analysis": {},
                  "priority_focus": {
                    "dimension": "grammar",
                    "reason": "grammar reason",
                    "action_item": "grammar action"
                  },
                  "summary": "summary"
                }
                """;
    }
}

