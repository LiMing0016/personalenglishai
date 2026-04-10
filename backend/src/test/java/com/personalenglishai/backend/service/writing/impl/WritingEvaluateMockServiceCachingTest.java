package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextRequest;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.TrustedRewriteService;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingEvaluateMockServiceCachingTest {

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

    private WritingEvaluateMockService newService() {
        return new WritingEvaluateMockService(
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
                new ScoreStagePromptPolicyRegistry(List.of(new PostgradExamScoreStagePromptPolicy()))
        );
    }

    @Test
    @DisplayName("重复评分时应只复用 prompt_cache_key，不复用 previous_response_id")
    void evaluateShouldNotReusePreviousResponseIdForScoring() {
        WritingEvaluateMockService service = newService();

        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setUserId(1L);
        request.setDocumentId("doc_1");
        request.setEssay("The picture shows students working together in a lab.");
        request.setMode("exam");
        request.setStudyStage("postgrad");
        request.setTaskType("task2");

        ScorePromptContext context = new ScorePromptContext(
                "doc_1",
                "gpt-4o",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "task prompt",
                "topic",
                160,
                200,
                25
        );
        ScoreRuntimeState runtimeState = new ScoreRuntimeState(
                "gpt-4o",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2",
                "resp_prev",
                service.computeEssayHash(request.getEssay())
        );

        RubricActiveResponse rubric = buildRubric("postgrad-exam-v1", "exam",
                "task_achievement", "content_quality", "structure", "vocabulary", "grammar", "expression");

        when(scorePromptContextResolver.resolve(eq(request), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(context);
        when(scorePromptCacheKeyBuilder.build(context))
                .thenReturn("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        when(runtimeStateService.get("doc_1")).thenReturn(runtimeState);
        when(rubricService.normalizeStage("postgrad")).thenReturn("postgrad");
        when(rubricService.normalizeMode("exam")).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(rubric);
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("rubric text");
        when(openAiClient.resolveModel(nullable(String.class))).thenReturn("gpt-4o");
        when(openAiClient.createTextResponse(any()))
                .thenReturn(new OpenAiResponsesTextResult("resp_new", aiResultJson(), 1500, 1200, 4096));
        when(grammarCheckService.check(request.getEssay(), "lite")).thenReturn(List.of());
        when(trustedRewriteService.filterTrustedTrinkaSuggestions(eq(1L), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        when(writingExamPolicyService.evaluate(eq("postgrad"), eq("exam"), eq("task2"), same(request), anyMap(), anyList()))
                .thenReturn(new WritingExamPolicyService.ExamPolicyResult(
                        "postgrad-exam-policy-v1", 75, 75, null, 0, Map.of(), List.of(), null
                ));
        when(abilityProfileMapper.selectByUserId(1L)).thenReturn(null);

        WritingEvaluateResponse response = service.evaluate(request);

        ArgumentCaptor<OpenAiResponsesTextRequest> requestCaptor = ArgumentCaptor.forClass(OpenAiResponsesTextRequest.class);
        verify(openAiClient).createTextResponse(requestCaptor.capture());
        assertThat(requestCaptor.getValue().previousResponseId()).isNull();
        assertThat(requestCaptor.getValue().promptCacheKey())
                .isEqualTo("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        assertThat(response.getInputTokens()).isEqualTo(1500);
        assertThat(response.getCachedTokens()).isEqualTo(1200);
        assertThat(response.getPromptCacheKey())
                .isEqualTo("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        assertThat(response.getInstructionsHash()).isNotBlank();
        assertThat(response.getCachedPrefixHash()).isNotBlank();
        assertThat(response.getEssayHash()).isEqualTo(service.computeEssayHash(request.getEssay()));
        assertThat(response.getCacheHitRate()).isNotNull();
    }

    @Test
    @DisplayName("诊断哈希应能区分稳定前缀与作文改动")
    void diagnosticHashesShouldSeparateStablePrefixAndEssayChanges() {
        WritingEvaluateMockService service = newService();

        ScorePromptContext context = new ScorePromptContext(
                "doc_1",
                "gpt-4o",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "task prompt",
                "topic",
                160,
                200,
                25
        );

        String instructionsA = ReflectionTestUtils.invokeMethod(service, "buildSystemPrompt", "postgrad", "exam", "task2");
        String prefixA = ReflectionTestUtils.invokeMethod(service, "buildCachedPromptPrefix", context, "rubric text");
        String essayHashA = service.computeEssayHash("Essay version A.");
        String essayHashB = service.computeEssayHash("Essay version B.");

        String instructionsHashA = ReflectionTestUtils.invokeMethod(service, "computePromptSegmentHash", instructionsA);
        String instructionsHashB = ReflectionTestUtils.invokeMethod(service, "computePromptSegmentHash", instructionsA);
        String prefixHashA = ReflectionTestUtils.invokeMethod(service, "computePromptSegmentHash", prefixA);
        String prefixHashB = ReflectionTestUtils.invokeMethod(service, "computePromptSegmentHash", prefixA);

        assertThat(instructionsHashA).isEqualTo(instructionsHashB);
        assertThat(prefixHashA).isEqualTo(prefixHashB);
        assertThat(essayHashA).isNotEqualTo(essayHashB);
    }

    @Test
    @DisplayName("doc 元数据为 postgrad 时应使用 postgrad rubric")
    void evaluateShouldUseDocumentStageRubricBeforeFallbackToRequestStage() {
        WritingEvaluateMockService service = newService();

        WritingEvaluateRequest request = new WritingEvaluateRequest();
        request.setUserId(7L);
        request.setDocumentId("doc_postgrad");
        request.setEssay("The chart shows meaningful gains from the labor practice course.");
        request.setMode("exam");

        WritingSessionMetadataResponse metadata = new WritingSessionMetadataResponse();
        metadata.setDocumentId("doc_postgrad");
        metadata.setMode("exam");
        metadata.setStudyStage("postgrad");
        metadata.setTaskType("task2");
        metadata.setPromptText("describe and interpret the chart");
        metadata.setTopicTitle("topic");
        metadata.setMinWords(150);
        metadata.setRecommendedMaxWords(180);
        metadata.setMaxScore(15);

        ScorePromptContext context = new ScorePromptContext(
                "doc_postgrad",
                "gpt-4o",
                "score-v1",
                "postgrad-exam-v1",
                "postgrad",
                "exam",
                "task2",
                "task-hash",
                "rubric-hash",
                "describe and interpret the chart",
                "topic",
                150,
                180,
                15
        );
        RubricActiveResponse rubric = buildRubric("postgrad-exam-v1", "exam",
                "task_achievement", "content_quality", "structure", "vocabulary", "grammar", "expression");

        when(documentService.getSessionMetadataByDocId("7", "default", "doc_postgrad", 7L))
                .thenReturn(metadata);
        when(rubricService.normalizeStage("postgrad")).thenReturn("postgrad");
        when(rubricService.normalizeMode("exam")).thenReturn("exam");
        when(rubricService.getActiveRubric("postgrad", "exam")).thenReturn(rubric);
        when(rubricTextBuilder.buildRubricText("postgrad", "exam")).thenReturn("rubric text");
        when(scorePromptContextResolver.resolve(eq(request), eq("gpt-4o"), eq("score-v1"), eq("postgrad-exam-v1"), anyString()))
                .thenReturn(context);
        when(scorePromptCacheKeyBuilder.build(context))
                .thenReturn("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        when(runtimeStateService.get("doc_postgrad")).thenReturn(null);
        when(openAiClient.resolveModel(nullable(String.class))).thenReturn("gpt-4o");
        when(openAiClient.createTextResponse(any()))
                .thenReturn(new OpenAiResponsesTextResult("resp_new", aiResultJson(), 1500, 0, 4096));
        when(grammarCheckService.check(request.getEssay(), "lite")).thenReturn(List.of());
        when(trustedRewriteService.filterTrustedTrinkaSuggestions(eq(7L), eq("doc_postgrad"), eq(request.getEssay()), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(3));
        when(writingExamPolicyService.evaluate(eq("postgrad"), eq("exam"), eq("task2"), same(request), anyMap(), anyList()))
                .thenReturn(new WritingExamPolicyService.ExamPolicyResult(
                        "postgrad-exam-policy-v1", 80, 80, null, 0, Map.of(), List.of(), null
                ));
        when(abilityProfileMapper.selectByUserId(7L)).thenReturn(null);

        WritingEvaluateResponse response = service.evaluate(request);

        assertThat(response.getPromptCacheKey())
                .isEqualTo("score:gpt-4o:score-v1:postgrad-exam-v1:postgrad:exam:task2");
        verify(rubricService).getActiveRubric("postgrad", "exam");
        verify(rubricService, never()).getActiveRubric("highschool", "exam");
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
