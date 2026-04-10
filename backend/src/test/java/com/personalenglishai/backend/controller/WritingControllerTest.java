package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.EssayFavoriteMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import com.personalenglishai.backend.service.writing.AuditTopicService;
import com.personalenglishai.backend.service.writing.WritingChatService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import com.personalenglishai.backend.service.writing.WritingTranslateService;
import com.personalenglishai.backend.service.writing.WritingTemplateService;
import com.personalenglishai.backend.service.writing.WritingMaterialService;
import com.personalenglishai.backend.service.writing.WritingModelEssayService;
import com.personalenglishai.backend.service.writing.WritingExamPromptService;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.GrammarSuppressService;
import com.personalenglishai.backend.service.writing.EssayPromptService;
import com.personalenglishai.backend.service.writing.TrustedRewriteService;
import com.personalenglishai.backend.service.writing.impl.WritingSuggestionsService;
import com.personalenglishai.backend.dto.writing.WritingTemplateRequest;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse;
import com.personalenglishai.backend.dto.writing.WritingMaterialRequest;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.dto.writing.WritingModelEssayResponse;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WritingController.class)
@AutoConfigureMockMvc(addFilters = false)
class WritingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WritingEvaluateService writingEvaluateService;

    @MockBean
    private WritingEvaluateTaskService writingEvaluateTaskService;

    @MockBean
    private WritingChatService writingChatService;

    @MockBean
    private WritingPolishService writingPolishService;

    @MockBean
    private WritingTranslateService writingTranslateService;

    @MockBean
    private GrammarCheckService grammarCheckService;

    @MockBean
    private GrammarSuppressService grammarSuppressService;

    @MockBean
    private TrustedRewriteService trustedRewriteService;

    @MockBean
    private WritingSuggestionsService writingSuggestionsService;

    @MockBean
    private AuditTopicService auditTopicService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private EssayEvaluationMapper essayEvaluationMapper;

    @MockBean
    private EssayFavoriteMapper essayFavoriteMapper;

    @MockBean
    private EssayPromptService essayPromptService;

    @MockBean
    private WritingTemplateService writingTemplateService;

    @MockBean
    private WritingMaterialService writingMaterialService;

    @MockBean
    private WritingModelEssayService writingModelEssayService;

    @MockBean
    private WritingExamPromptService writingExamPromptService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    private static final String VALID_ESSAY =
            "Last weekend I went to the park with my friends. We had a wonderful time there. "
            + "The weather was sunny and warm. We played football and flew kites happily. "
            + "I think outdoor activities are very important for students because they help us relax.";

    @Nested
    @DisplayName("POST /api/writing/evaluate")
    class Evaluate {

        @Test
        @DisplayName("returns 200 with valid essay")
        void evaluate_success() throws Exception {
            WritingEvaluateResponse mockResponse = new WritingEvaluateResponse();
            mockResponse.setRequestId("eval-test");
            mockResponse.setMode("free");
            mockResponse.setSummary("Good job");
            when(writingEvaluateService.evaluate(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/api/writing/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content(objectMapper.writeValueAsString(
                                    buildRequest(VALID_ESSAY, "free"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value("eval-test"))
                    .andExpect(jsonPath("$.mode").value("free"));
        }

        @Test
        @DisplayName("rejects essay that is too short (< 20 words)")
        void evaluate_tooShort() throws Exception {
            mockMvc.perform(post("/api/writing/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content(objectMapper.writeValueAsString(
                                    buildRequest("Too short essay.", "free"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400010"));
        }

        @Test
        @DisplayName("rejects essay that is too long (> 500 words)")
        void evaluate_tooLong() throws Exception {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 510; i++) sb.append("word ");
            mockMvc.perform(post("/api/writing/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content(objectMapper.writeValueAsString(
                                    buildRequest(sb.toString(), "free"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400011"));
        }

        @Test
        @DisplayName("rejects blank essay")
        void evaluate_blank() throws Exception {
            mockMvc.perform(post("/api/writing/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{\"essay\":\"\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/evaluate/submit")
    class SubmitEvaluate {

        @Test
        @DisplayName("returns 202 accepted with valid essay")
        void submit_success() throws Exception {
            WritingEvaluateTaskResponse mockResponse = new WritingEvaluateTaskResponse();
            mockResponse.setRequestId("eval-task-abc123");
            mockResponse.setStatus("processing");
            when(writingEvaluateTaskService.submit(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/api/writing/evaluate/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content(objectMapper.writeValueAsString(
                                    buildRequest(VALID_ESSAY, "free"))))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.requestId").value("eval-task-abc123"))
                    .andExpect(jsonPath("$.status").value("processing"));
        }
    }

    @Nested
    @DisplayName("GET /api/writing/evaluate/tasks/{requestId}")
    class GetTask {

        @Test
        @DisplayName("returns 200 when task exists")
        void getTask_found() throws Exception {
            WritingEvaluateTaskResponse mockResponse = new WritingEvaluateTaskResponse();
            mockResponse.setRequestId("eval-task-abc123");
            mockResponse.setStatus("succeeded");
            mockResponse.setUserId(1L);
            when(writingEvaluateTaskService.getTask("eval-task-abc123")).thenReturn(mockResponse);

            mockMvc.perform(get("/api/writing/evaluate/tasks/eval-task-abc123")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("succeeded"));
        }

        @Test
        @DisplayName("returns 404 when task not found")
        void getTask_notFound() throws Exception {
            when(writingEvaluateTaskService.getTask("nonexistent")).thenReturn(null);

            mockMvc.perform(get("/api/writing/evaluate/tasks/nonexistent")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 404 when task belongs to another user")
        void getTask_otherUser() throws Exception {
            WritingEvaluateTaskResponse mockResponse = new WritingEvaluateTaskResponse();
            mockResponse.setRequestId("eval-task-other");
            mockResponse.setStatus("succeeded");
            mockResponse.setUserId(2L);
            when(writingEvaluateTaskService.getTask("eval-task-other")).thenReturn(mockResponse);

            mockMvc.perform(get("/api/writing/evaluate/tasks/eval-task-other")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 401 when userId is missing")
        void getTask_noAuth() throws Exception {
            mockMvc.perform(get("/api/writing/evaluate/tasks/eval-task-any"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/grammar-check")
    class GrammarCheck {

        @Test
        @DisplayName("透传 trinkaMode 到语法检查服务")
        void grammarCheck_passesTrinkaMode() throws Exception {
            when(grammarCheckService.check(any(), any())).thenReturn(List.of());
            when(grammarSuppressService.filterSuppressed(any(), any(), any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(2));
            when(trustedRewriteService.filterTrustedTrinkaSuggestions(any(), any(), any(), any()))
                    .thenAnswer(invocation -> invocation.getArgument(3));

            mockMvc.perform(post("/api/writing/grammar-check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{" +
                                    "\"text\":\"This is a valid paragraph for grammar check with enough words.\"," +
                                    "\"docId\":\"doc-1\"," +
                                    "\"trinkaMode\":\"power\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"errors\":[]}"));

            verify(grammarCheckService).check(
                    eq("This is a valid paragraph for grammar check with enough words."),
                    eq("power"));
        }
    }

    @Nested
    @DisplayName("GET /api/writing/history")
    class History {

        @Test
        @DisplayName("returns 401 when userId is missing")
        void history_noAuth() throws Exception {
            mockMvc.perform(get("/api/writing/history"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns paged items with total")
        void history_success() throws Exception {
            EssayEvaluation record = new EssayEvaluation();
            record.setId(10L);
            record.setUserId(1L);
            record.setMode("free");
            record.setGaokaoScore(11);
            record.setMaxScore(15);
            record.setBand("good");
            record.setOverallScore(78);
            record.setEssayText("This is a test essay with enough words for preview generation.");
            record.setCreatedAt(LocalDateTime.of(2026, 3, 3, 12, 0));

            when(essayEvaluationMapper.selectByUserId(1L, 0, 10)).thenReturn(List.of(record));
            when(essayEvaluationMapper.countByUserId(1L)).thenReturn(1L);
            when(essayFavoriteMapper.selectEvalIdsByUserId(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/writing/history?page=0&size=10")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(10))
                    .andExpect(jsonPath("$.items[0].mode").value("free"))
                    .andExpect(jsonPath("$.items[0].gaokao_score").value(11))
                    .andExpect(jsonPath("$.items[0].max_score").value(15));
        }

        @Test
        @DisplayName("caps page size at 50")
        void history_capsPageSize() throws Exception {
            when(essayEvaluationMapper.selectByUserId(1L, 0, 50)).thenReturn(List.of());
            when(essayEvaluationMapper.countByUserId(1L)).thenReturn(0L);
            when(essayFavoriteMapper.selectEvalIdsByUserId(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/writing/history?page=0&size=999")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"items\":[],\"total\":0}"));
        }
    }

    @Nested
    @DisplayName("POST /api/writing/generate-exam-prompt")
    class GenerateExamPrompt {

        @Test
        @DisplayName("returns structured AI exam prompt preview")
        void generateExamPrompt_success() throws Exception {
            GenerateExamPromptResponse response = new GenerateExamPromptResponse();
            response.setPromptType("chart");
            response.setTopic("人工智能学习工具使用变化");
            response.setPromptText("Write an essay based on the table below.");
            response.setRequirements("describe the changes and give your comments");
            response.setSourceType("ai_generated");
            response.setTaskType("task1");
            response.setPromptSheetId(501L);
            response.setPaper("ai-20260410-abc12345");
            GenerateExamPromptResponse.ChartSpec chartSpec = new GenerateExamPromptResponse.ChartSpec();
            chartSpec.setTitle("AI Study Tool Usage");
            chartSpec.setColumns(List.of("Year", "Usage Rate"));
            chartSpec.setRows(List.of(List.of("2021", "18%"), List.of("2024", "63%")));
            response.setChartSpec(chartSpec);
            when(writingExamPromptService.generate(any())).thenReturn(response);

            mockMvc.perform(post("/api/writing/generate-exam-prompt")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("""
                                    {
                                      "originalInput":"请给我出一道图表题，内容是 2021-2024 年大学生使用 AI 学习工具的比例变化。",
                                      "studyStage":"postgrad",
                                      "topic":"人工智能学习工具使用变化",
                                      "promptType":"chart",
                                      "requirements":"突出近四年变化趋势",
                                      "wordRange":"160-200",
                                      "maxScore":20
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.promptType").value("chart"))
                    .andExpect(jsonPath("$.sourceType").value("ai_generated"))
                    .andExpect(jsonPath("$.taskType").value("task1"))
                    .andExpect(jsonPath("$.promptSheetId").value(501))
                    .andExpect(jsonPath("$.paper").value("ai-20260410-abc12345"))
                    .andExpect(jsonPath("$.chartSpec.columns[0]").value("Year"))
                    .andExpect(jsonPath("$.chartSpec.rows[1][1]").value("63%"));
        }
    }

    @Nested
    @DisplayName("POST /api/writing/start-session")
    class StartSession {

        @Test
        @DisplayName("passes generated attachment image url into writing session metadata")
        void startSession_passesAttachmentImageUrl() throws Exception {
            Constructor<DocumentService.StartSessionResult> constructor = DocumentService.StartSessionResult.class
                    .getDeclaredConstructor(String.class, int.class, boolean.class, String.class, Integer.class, Integer.class, Integer.class);
            constructor.setAccessible(true);
            DocumentService.StartSessionResult result = constructor.newInstance("doc-1", 1, true, null, null, null, 0);

            when(documentService.findOrCreateForTopic(any(), any(), any(), any(), any(), any(), any())).thenReturn(result);
            when(documentService.findByPublicId(any(), any(), any())).thenReturn(null);
            when(documentService.getSessionMetadataByDocId(any(), any(), any(), any())).thenReturn(null);

            mockMvc.perform(post("/api/writing/start-session")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("""
                                    {
                                      "mode":"exam",
                                      "taskPrompt":"Write an essay based on the line chart below.",
                                      "title":"折线图作文",
                                      "promptText":"Write an essay based on the line chart below.",
                                      "sourceType":"ai_generated",
                                      "promptSheetId":501,
                                      "attachmentImageUrl":"https://example.com/generated-line-chart.png"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.docId").value("doc-1"));

            ArgumentCaptor<DocumentService.StartMetadata> metadataCaptor = ArgumentCaptor.forClass(DocumentService.StartMetadata.class);
            verify(documentService, times(1)).findOrCreateForTopic(any(), any(), any(), any(), any(), any(), metadataCaptor.capture());
            org.junit.jupiter.api.Assertions.assertEquals(
                    "https://example.com/generated-line-chart.png",
                    metadataCaptor.getValue().getAttachmentImageUrl()
            );
            org.junit.jupiter.api.Assertions.assertEquals(
                    501L,
                    metadataCaptor.getValue().getPromptSheetId()
            );
        }
    }

    @Nested
    @DisplayName("GET /api/writing/history/{id}")
    class HistoryDetail {

        @Test
        @DisplayName("returns 401 when userId is missing")
        void detail_noAuth() throws Exception {
            mockMvc.perform(get("/api/writing/history/100"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 404 when record not found")
        void detail_notFound() throws Exception {
            when(essayEvaluationMapper.selectById(100L)).thenReturn(null);

            mockMvc.perform(get("/api/writing/history/100")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 404 when record belongs to another user")
        void detail_otherUser() throws Exception {
            EssayEvaluation record = new EssayEvaluation();
            record.setId(100L);
            record.setUserId(2L);
            when(essayEvaluationMapper.selectById(100L)).thenReturn(record);

            mockMvc.perform(get("/api/writing/history/100")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns full detail when record belongs to current user")
        void detail_success() throws Exception {
            WritingEvaluateResponse eval = new WritingEvaluateResponse();
            eval.setRequestId("eval-100");
            eval.setMode("free");
            eval.setSummary("Well done");

            EssayEvaluation record = new EssayEvaluation();
            record.setId(100L);
            record.setUserId(1L);
            record.setEssayText("Original essay text");
            record.setResultJson(objectMapper.writeValueAsString(eval));
            when(essayEvaluationMapper.selectById(100L)).thenReturn(record);

            mockMvc.perform(get("/api/writing/history/100")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.essayText").value("Original essay text"))
                    .andExpect(jsonPath("$.result.requestId").value("eval-100"))
                    .andExpect(jsonPath("$.result.summary").value("Well done"));
        }
    }

    @Nested
    @DisplayName("POST /api/writing/template")
    class Template {

        @Test
        @DisplayName("returns 200 with valid text")
        void template_success() throws Exception {
            WritingTemplateResponse mockResponse = new WritingTemplateResponse();
            mockResponse.setParagraphs(List.of());
            mockResponse.setUsageTips(List.of());
            when(writingTemplateService.extract(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/api/writing/template")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{\"text\":\"" + VALID_ESSAY + "\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("rejects blank text")
        void template_blankText() throws Exception {
            mockMvc.perform(post("/api/writing/template")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{\"text\":\"\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/material")
    class Material {

        @Test
        @DisplayName("returns 200 with valid taskPrompt")
        void material_success() throws Exception {
            WritingMaterialResponse mockResponse = new WritingMaterialResponse();
            mockResponse.setVocabulary(List.of());
            mockResponse.setPhrases(List.of());
            mockResponse.setSentences(List.of());
            when(writingMaterialService.generate(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/api/writing/material")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{\"taskPrompt\":\"Discuss the impact of AI on education\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("rejects blank taskPrompt")
        void material_blankPrompt() throws Exception {
            mockMvc.perform(post("/api/writing/material")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{\"taskPrompt\":\"\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/model-essay")
    class ModelEssay {

        @Test
        @DisplayName("returns two learning cards")
        void modelEssay_success() throws Exception {
            WritingModelEssayResponse mockResponse = new WritingModelEssayResponse();
            WritingModelEssayResponse.ModelEssayCard excellent = new WritingModelEssayResponse.ModelEssayCard();
            excellent.setLabel("优秀作文");
            excellent.setEssay("This is an excellent essay.");
            excellent.setHighScoreReasons(List.of("结构：层次清楚"));
            excellent.setImprovementGuidance(List.of("任务完成：补强评论"));
            mockResponse.setExcellentEssay(excellent);

            WritingModelEssayResponse.ModelEssayCard perfect = new WritingModelEssayResponse.ModelEssayCard();
            perfect.setLabel("满分作文");
            perfect.setEssay("This is a perfect essay.");
            perfect.setHighScoreReasons(List.of("内容质量：分析充分"));
            perfect.setImprovementGuidance(List.of("内容质量：补足细节"));
            mockResponse.setPerfectEssay(perfect);
            when(writingModelEssayService.generate(any())).thenReturn(mockResponse);

            mockMvc.perform(post("/api/writing/model-essay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("""
                                    {
                                      "essay": "This is a student essay with enough words for model essay generation.",
                                      "studyStage": "postgrad",
                                      "writingMode": "exam",
                                      "taskType": "task2",
                                      "topicContent": "图表显示某市公园数量增长。",
                                      "taskPrompt": "describe and comment on the chart"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.excellentEssay.label").value("优秀作文"))
                    .andExpect(jsonPath("$.perfectEssay.label").value("满分作文"))
                    .andExpect(jsonPath("$.excellentEssay.highScoreReasons[0]").value("结构：层次清楚"));
        }

        @Test
        @DisplayName("rejects blank essay")
        void modelEssay_blankEssay() throws Exception {
            mockMvc.perform(post("/api/writing/model-essay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("{\"essay\":\"\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    private WritingEvaluateRequest buildRequest(String essay, String mode) {
        WritingEvaluateRequest req = new WritingEvaluateRequest();
        req.setEssay(essay);
        req.setMode(mode);
        return req;
    }
}
