package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.service.document.DocumentService;
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
    @Mock private DocumentService documentService;
    @Mock private DefaultScorePromptContextResolver scorePromptContextResolver;
    @Mock private DefaultScorePromptCacheKeyBuilder scorePromptCacheKeyBuilder;
    @Mock private RedisScoreRuntimeStateService runtimeStateService;
    private ScoreStagePromptPolicyRegistry scoreStagePromptPolicyRegistry;

    private WritingEvaluateMockService service;

    @BeforeEach
    void setUp() {
        scoreStagePromptPolicyRegistry = new ScoreStagePromptPolicyRegistry(List.of(new PostgradExamScoreStagePromptPolicy()));
        service = new WritingEvaluateMockService(
                rubricService,
                rubricTextBuilder,
                openAiClient,
                new ObjectMapper(),
                abilityProfileMapper,
                writingEvaluationPersistenceService,
                writingExamPolicyService,
                grammarCheckService,
                trustedRewriteService,
                documentService,
                scorePromptContextResolver,
                scorePromptCacheKeyBuilder,
                runtimeStateService,
                scoreStagePromptPolicyRegistry
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
    @DisplayName("正式评分 instructions 应只保留高优先级判卷原则")
    void scoringInstructionsShouldStayLean() {
        String instructions = ReflectionTestUtils.invokeMethod(
                service,
                "buildSystemPrompt",
                "postgrad",
                "exam",
                "task2"
        );

        assertThat(instructions)
                .contains("你是一位严格的英语写作评分老师")
                .contains("必须先判任务完成度与切题性")
                .contains("只输出合法 JSON")
                .doesNotContain("[RUBRIC_FROM_DB]")
                .doesNotContain("量化锚点")
                .doesNotContain("strength_quote")
                .doesNotContain("weakness_quote");
    }

    @Test
    @DisplayName("highschool 场景不应混入 postgrad 专项文案")
    void highschoolPromptShouldNotContainPostgradSpecificRules() {
        String instructions = ReflectionTestUtils.invokeMethod(
                service,
                "buildSystemPrompt",
                "highschool",
                "exam",
                "task2"
        );
        String taskSection = ReflectionTestUtils.invokeMethod(
                service,
                "buildTaskSection",
                "highschool",
                "exam",
                "task2",
                true
        );

        assertThat(instructions)
                .doesNotContain("postgrad exam")
                .doesNotContain("考研评分场景");
        assertThat(taskSection)
                .doesNotContain("考研 task2 判分锚点");
    }

    @Test
    @DisplayName("动态尾部应只保留作文正文")
    void dynamicTailShouldOnlyContainEssay() {
        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setEssay("The picture shows two young people planting a tree together.");
        request.setAiHint("请重点看看语法");
        ScorePromptContext context = new ScorePromptContext(
                "doc-1",
                "gpt-test",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "Describe the picture, explain its meaning, and give your comment.",
                "Tree planting",
                150,
                180,
                15
        );

        String suffix = ReflectionTestUtils.invokeMethod(
                service,
                "buildDynamicPromptSuffix",
                request,
                context,
                "exam",
                "postgrad",
                "task2"
        );

        assertThat(suffix)
                .contains("[ESSAY]")
                .contains("The picture shows two young people planting a tree together.")
                .doesNotContain("[文本统计]")
                .doesNotContain("[DOCUMENT_CONTEXT]")
                .doesNotContain("教师补充提示")
                .doesNotContain("topic_title=")
                .doesNotContain("min_words=");
    }

    @Test
    @DisplayName("缓存前缀应包含稳定题目要求与文档上下文")
    void cachedPrefixShouldContainTaskRequirementsAndDocumentContext() {
        ScorePromptContext context = new ScorePromptContext(
                "doc-1",
                "gpt-test",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "Describe the picture, explain its meaning, and give your comment.",
                "Tree planting",
                150,
                180,
                15
        );

        String prefix = ReflectionTestUtils.invokeMethod(
                service,
                "buildCachedPromptPrefix",
                context,
                "rubric text"
        );

        assertThat(prefix)
                .contains("[TASK_REQUIREMENTS]")
                .contains("[DOCUMENT_CONTEXT]")
                .contains("topic_title=Tree planting")
                .contains("min_words=150")
                .contains("recommended_max_words=180")
                .contains("max_score=15");
    }

    @Test
    @DisplayName("评分 Prompt 不应再要求 GPT 生成 errors 数组")
    void scoringPromptShouldNotAskGptToGenerateErrorsArray() {
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
        String schema = ReflectionTestUtils.invokeMethod(service, "buildOutputSchema", "exam");

        assertThat(prompt)
                .doesNotContain("errors[]")
                .doesNotContain("errors 数组")
                .doesNotContain("逐条错误放在 errors 数组中");
        assertThat(schema)
                .doesNotContain("\"errors\"")
                .doesNotContain("category")
                .doesNotContain("severity");
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
        when(openAiClient.createTextResponse(any()))
                .thenReturn(new OpenAiResponsesTextResult("resp_1", aiResultJson(), 1200, 900, 2048));
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
        when(openAiClient.resolveModel(null)).thenReturn("gpt-test");
        when(scorePromptContextResolver.resolve(eq(request), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ScorePromptContext(
                        "doc-1",
                        "gpt-test",
                        "score-v1",
                        "postgrad-exam-v1",
                        "postgrad",
                        "exam",
                        "task2",
                        "task-hash",
                        "rubric-hash",
                        request.getTaskPrompt(),
                        request.getTopicTitle(),
                        request.getMinWords(),
                        request.getRecommendedMaxWords(),
                        request.getMaxScore()
                ));
        when(scorePromptCacheKeyBuilder.build(any()))
                .thenReturn("score:gpt-test:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        when(runtimeStateService.get("doc-1")).thenReturn(null);

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
        when(openAiClient.resolveModel(null)).thenReturn("gpt-test");

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
                "postgrad-exam-v1",
                null
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

