package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.mapper.DocumentScoreSummaryMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.EssayFavoriteMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import com.personalenglishai.backend.service.writing.AuditTopicService;
import com.personalenglishai.backend.service.writing.WritingChatService;
import com.personalenglishai.backend.service.writing.WritingDashboardService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import com.personalenglishai.backend.service.writing.HandwritingRecognitionService;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import com.personalenglishai.backend.service.writing.WritingTranslateService;
import com.personalenglishai.backend.service.writing.WritingTemplateService;
import com.personalenglishai.backend.service.writing.WritingMaterialService;
import com.personalenglishai.backend.service.writing.WritingModelEssayService;
import com.personalenglishai.backend.service.writing.WritingExamDialogueService;
import com.personalenglishai.backend.service.writing.WritingExamPromptService;
import com.personalenglishai.backend.service.writing.PromptSheetChatService;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.GrammarSuppressService;
import com.personalenglishai.backend.service.writing.EssayPromptService;
import com.personalenglishai.backend.service.writing.TrustedRewriteService;
import com.personalenglishai.backend.service.writing.impl.WritingSuggestionsService;
import com.personalenglishai.backend.dto.writing.WritingTemplateRequest;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse;
import com.personalenglishai.backend.dto.writing.BindHandwritingImportRequest;
import com.personalenglishai.backend.dto.writing.GenerateExamDialogueTurnResponse;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageRequest;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageResponse;
import com.personalenglishai.backend.dto.writing.WritingMaterialRequest;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse;
import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import com.personalenglishai.backend.dto.writing.WritingModelEssayResponse;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private DocumentScoreSummaryMapper documentScoreSummaryMapper;

    @MockBean
    private WritingDashboardService writingDashboardService;

    @MockBean
    private EssayPromptService essayPromptService;

    @MockBean
    private HandwritingRecognitionService handwritingRecognitionService;

    @MockBean
    private WritingTemplateService writingTemplateService;

    @MockBean
    private WritingMaterialService writingMaterialService;

    @MockBean
    private WritingModelEssayService writingModelEssayService;

    @MockBean
    private WritingExamPromptService writingExamPromptService;

    @MockBean
    private PromptSheetChatService promptSheetChatService;

    @MockBean
    private WritingExamDialogueService writingExamDialogueService;

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
    @DisplayName("GET /api/writing/dashboard/assets")
    class DashboardAssets {

        @Test
        @DisplayName("returns summary and monthly series for all modes")
        void dashboardAssets_success() throws Exception {
            when(writingDashboardService.buildAssetDashboard(1L, "all", "month"))
                    .thenReturn(assetDashboardResponse(
                            Map.of(
                                    "totalEssays", 3,
                                    "totalWords", 1010,
                                    "totalSentences", 66,
                                    "avgGrammarErrorsPerEssay", 4.0
                            ),
                            List.of(
                                    periodRow("2026-03-01", "3月", 600, 40, 2),
                                    periodRow("2026-04-01", "4月", 410, 26, 1)
                            )
                    ));

            mockMvc.perform(get("/api/writing/dashboard/assets?mode=all&granularity=month")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalEssays").value(3))
                    .andExpect(jsonPath("$.summary.totalWords").value(1010))
                    .andExpect(jsonPath("$.summary.totalSentences").value(66))
                    .andExpect(jsonPath("$.summary.avgGrammarErrorsPerEssay").value(4.0))
                    .andExpect(jsonPath("$.series[0].periodLabel").value("3月"))
                    .andExpect(jsonPath("$.series[0].wordCount").value(600))
                    .andExpect(jsonPath("$.series[0].sentenceCount").value(40))
                    .andExpect(jsonPath("$.series[0].essayCount").value(2))
                    .andExpect(jsonPath("$.series[1].periodLabel").value("4月"))
                    .andExpect(jsonPath("$.series[1].wordCount").value(410))
                    .andExpect(jsonPath("$.series[1].sentenceCount").value(26))
                    .andExpect(jsonPath("$.series[1].essayCount").value(1));
        }

        @Test
        @DisplayName("filters exam mode and groups weekly")
        void dashboardAssets_weeklyExamMode() throws Exception {
            when(writingDashboardService.buildAssetDashboard(1L, "exam", "week"))
                    .thenReturn(assetDashboardResponse(
                            Map.of(
                                    "totalEssays", 2,
                                    "totalWords", 720,
                                    "totalSentences", 47,
                                    "avgGrammarErrorsPerEssay", 4.5
                            ),
                            List.of(
                                    periodRow("2026-04-06", "4/6", 300, 20, 1),
                                    periodRow("2026-04-13", "4/13", 420, 27, 1)
                            )
                    ));

            mockMvc.perform(get("/api/writing/dashboard/assets?mode=exam&granularity=week")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalEssays").value(2))
                    .andExpect(jsonPath("$.series.length()").value(2))
                    .andExpect(jsonPath("$.series[0].periodLabel").value("4/6"))
                    .andExpect(jsonPath("$.series[1].periodLabel").value("4/13"));
        }

        @Test
        @DisplayName("returns 401 when userId is missing")
        void dashboardAssets_noAuth() throws Exception {
            mockMvc.perform(get("/api/writing/dashboard/assets"))
                    .andExpect(status().isUnauthorized());
        }

        private Map<String, Object> assetDashboardResponse(Map<String, Object> summary, List<Map<String, Object>> series) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("summary", summary);
            response.put("series", series);
            return response;
        }

        private Map<String, Object> periodRow(
                String periodStart,
                String periodLabel,
                int wordCount,
                int sentenceCount,
                int essayCount
        ) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("periodStart", periodStart);
            row.put("periodLabel", periodLabel);
            row.put("wordCount", wordCount);
            row.put("sentenceCount", sentenceCount);
            row.put("essayCount", essayCount);
            return row;
        }
    }

    @Nested
    @DisplayName("POST /api/writing/generate-exam-dialogue-turn")
    class GenerateExamDialogueTurn {

        @Test
        @DisplayName("returns assistant reply blocks and draft preview status")
        void generateExamDialogueTurn_returnsAssistantReplyAndDraftPreview() throws Exception {
            GenerateExamDialogueTurnResponse response = new GenerateExamDialogueTurnResponse();
            response.setPreviewStatus("draft");
            response.setMissingFields(List.of("待补充字数"));

            GenerateExamDialogueTurnResponse.AssistantReplyBlock replyBlock =
                    new GenerateExamDialogueTurnResponse.AssistantReplyBlock();
            replyBlock.setKind("understanding");
            replyBlock.setText("我理解你想保留原题表述。");
            response.setAssistantReplyBlocks(List.of(replyBlock));

            GenerateExamPromptResponse promptSheetDraft = new GenerateExamPromptResponse();
            promptSheetDraft.setPromptType("comic");
            promptSheetDraft.setTopic("Write an essay based on the picture below.");
            promptSheetDraft.setPromptText("Write an essay based on the picture below.");
            promptSheetDraft.setRequirements("1) describe the picture briefly 2) interpret the meaning 3) give your comments");
            response.setPromptSheetDraft(promptSheetDraft);

            when(writingExamDialogueService.generateTurn(eq(1L), any())).thenReturn(response);

            mockMvc.perform(post("/api/writing/generate-exam-dialogue-turn")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "studyStage": "postgrad",
                                      "aiProvider": "openai",
                                      "selectedMode": "exam",
                                      "messages": [
                                        {
                                          "role": "user",
                                          "kind": "text",
                                          "text": "不要改图片原题"
                                        }
                                      ]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.previewStatus").value("draft"))
                    .andExpect(jsonPath("$.assistantReplyBlocks[0].text").value("我理解你想保留原题表述。"))
                    .andExpect(jsonPath("$.missingFields[0]").value("待补充字数"))
                    .andExpect(jsonPath("$.promptSheetDraft.promptType").value("comic"));
        }
    }

    @Nested
    @DisplayName("POST /api/writing/audit-topic")
    class AuditTopic {

        @Test
        @DisplayName("passes selected aiProvider to audit service")
        void auditTopic_passesAiProvider() throws Exception {
            var response = com.personalenglishai.backend.dto.writing.AuditTopicResponse.complete(
                    "Write an essay based on the picture below.",
                    "comic",
                    "看图作文",
                    "160-200",
                    "1) describe the picture briefly 2) interpret the meaning 3) give your comments"
            );
            when(auditTopicService.audit(any(), eq("openai"))).thenReturn(response);

            mockMvc.perform(post("/api/writing/audit-topic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "topic": "请识别这道作文题",
                                      "studyStage": "postgrad",
                                      "aiProvider": "openai"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.promptType").value("comic"));

            ArgumentCaptor<com.personalenglishai.backend.dto.writing.AuditTopicRequest> requestCaptor =
                    ArgumentCaptor.forClass(com.personalenglishai.backend.dto.writing.AuditTopicRequest.class);
            verify(auditTopicService).audit(requestCaptor.capture(), eq("openai"));
            assertEquals("请识别这道作文题", requestCaptor.getValue().getTopic());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/recognize-topic-image")
    class RecognizeTopicImage {

        @Test
        @DisplayName("passes selected aiProvider to image recognition service")
        void recognizeTopicImage_passesAiProvider() throws Exception {
            when(auditTopicService.recognizeImage("data:image/png;base64,abc", "openai"))
                    .thenReturn(new com.personalenglishai.backend.dto.writing.RecognizeTopicImageResponse("Directions..."));

            mockMvc.perform(post("/api/writing/recognize-topic-image")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "imageBase64": "data:image/png;base64,abc",
                                      "aiProvider": "openai"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").value("Directions..."));

            verify(auditTopicService).recognizeImage("data:image/png;base64,abc", "openai");
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
    @DisplayName("Handwriting import DTO contract")
    class HandwritingImportContract {

        @Test
        @DisplayName("serializes handwriting request and response fields")
        void handwritingDtos_useExpectedJsonFields() throws Exception {
            RecognizeHandwritingImageRequest recognizeRequest = new RecognizeHandwritingImageRequest();
            recognizeRequest.setImageBase64("data:image/png;base64,abc");
            recognizeRequest.setAiProvider("openai");

            BindHandwritingImportRequest bindRequest = new BindHandwritingImportRequest();
            bindRequest.setDocId("doc-1");
            bindRequest.setSourceType("image");
            bindRequest.setImageUrl("data:image/png;base64,abc");
            bindRequest.setRecognizedText("recognized text");

            RecognizeHandwritingImageResponse recognizeResponse =
                    new RecognizeHandwritingImageResponse(
                            "data:image/png;base64,abc",
                            "raw line 1",
                            "normalized paragraph 1",
                            new java.math.BigDecimal("0.82"));

            assertEquals(
                    "data:image/png;base64,abc",
                    objectMapper.readTree(objectMapper.writeValueAsString(recognizeRequest))
                            .get("imageBase64")
                            .asText());
            assertEquals(
                    "openai",
                    objectMapper.readTree(objectMapper.writeValueAsString(recognizeRequest))
                            .get("aiProvider")
                            .asText());
            assertEquals(
                    "doc-1",
                    objectMapper.readTree(objectMapper.writeValueAsString(bindRequest))
                            .get("docId")
                            .asText());
            assertEquals(
                    "image",
                    objectMapper.readTree(objectMapper.writeValueAsString(bindRequest))
                            .get("sourceType")
                            .asText());
            assertEquals(
                    "normalized paragraph 1",
                    objectMapper.readTree(objectMapper.writeValueAsString(recognizeResponse))
                            .get("normalizedText")
                            .asText());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/bind-handwriting-import")
    class BindHandwritingImport {

        @Test
        @DisplayName("binds latest handwriting metadata and returns session metadata")
        void bindHandwritingImport_success() throws Exception {
            WritingSessionMetadataResponse response = new WritingSessionMetadataResponse();
            response.setDocumentId("doc-1");
            response.setLatestHandwrittenSourceType("image");
            response.setLatestHandwrittenSourceImageUrl("https://example.com/handwriting.png");
            response.setLatestHandwrittenRecognizedText("recognized text");
            response.setLatestHandwrittenImportedAt(LocalDateTime.of(2026, 4, 12, 11, 0));
            when(documentService.getSessionMetadataByDocId(any(), any(), eq("doc-1"), eq(1L)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/writing/bind-handwriting-import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content("""
                                    {
                                      "docId":"doc-1",
                                      "sourceType":"image",
                                      "imageUrl":"https://example.com/handwriting.png",
                                      "recognizedText":"recognized text"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentId").value("doc-1"))
                    .andExpect(jsonPath("$.latestHandwrittenSourceType").value("image"))
                    .andExpect(jsonPath("$.latestHandwrittenSourceImageUrl")
                            .value("https://example.com/handwriting.png"))
                    .andExpect(jsonPath("$.latestHandwrittenRecognizedText").value("recognized text"))
                    .andExpect(jsonPath("$.latestHandwrittenImportedAt").value("2026-04-12T11:00:00"));

            ArgumentCaptor<BindHandwritingImportRequest> requestCaptor =
                    ArgumentCaptor.forClass(BindHandwritingImportRequest.class);
            verify(documentService).bindHandwritingImport(eq("1"), eq("default"), requestCaptor.capture(), eq(1L));
            assertEquals("doc-1", requestCaptor.getValue().getDocId());
            assertEquals("image", requestCaptor.getValue().getSourceType());
            assertEquals("https://example.com/handwriting.png", requestCaptor.getValue().getImageUrl());
            assertEquals("recognized text", requestCaptor.getValue().getRecognizedText());
        }

        @Test
        @DisplayName("returns 401 when userId is missing")
        void bindHandwritingImport_noAuth() throws Exception {
            mockMvc.perform(post("/api/writing/bind-handwriting-import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "docId":"doc-1",
                                      "imageUrl":"https://example.com/handwriting.png",
                                      "recognizedText":"recognized text"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/writing/recognize-handwriting-image")
    class RecognizeHandwritingImage {

        @Test
        @DisplayName("returns structured result from handwriting recognition service")
        void recognizeHandwritingImage_success() throws Exception {
            RecognizeHandwritingImageResponse response =
                    new RecognizeHandwritingImageResponse(
                            "data:image/png;base64,abc",
                            "raw line 1",
                            "normalized paragraph 1",
                            new java.math.BigDecimal("0.82"));
            when(handwritingRecognitionService.recognize(any())).thenReturn(response);

            mockMvc.perform(post("/api/writing/recognize-handwriting-image")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "imageBase64":"data:image/png;base64,abc",
                                      "aiProvider":"openai"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imageUrl").value("data:image/png;base64,abc"))
                    .andExpect(jsonPath("$.recognizedText").value("raw line 1"))
                    .andExpect(jsonPath("$.normalizedText").value("normalized paragraph 1"))
                    .andExpect(jsonPath("$.confidence").value(0.82));

            ArgumentCaptor<RecognizeHandwritingImageRequest> requestCaptor =
                    ArgumentCaptor.forClass(RecognizeHandwritingImageRequest.class);
            verify(handwritingRecognitionService).recognize(requestCaptor.capture());
            assertEquals("data:image/png;base64,abc", requestCaptor.getValue().getImageBase64());
            assertEquals("openai", requestCaptor.getValue().getAiProvider());
        }
    }

    @Nested
    @DisplayName("GET /api/writing/documents/{docId}/metadata")
    class SessionMetadata {

        @Test
        @DisplayName("returns latest handwritten source fields")
        void metadata_includesLatestHandwrittenFields() throws Exception {
            WritingSessionMetadataResponse response = new WritingSessionMetadataResponse();
            response.setDocumentId("doc-1");
            response.setLatestHandwrittenSourceType("image");
            response.setLatestHandwrittenSourceImageUrl("data:image/png;base64,abc");
            response.setLatestHandwrittenRecognizedText("handwritten source text");
            response.setLatestHandwrittenImportedAt(LocalDateTime.of(2026, 4, 12, 10, 30));
            when(documentService.getSessionMetadataByDocId(any(), any(), eq("doc-1"), eq(1L)))
                    .thenReturn(response);

            mockMvc.perform(get("/api/writing/documents/doc-1/metadata")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.latestHandwrittenSourceType").value("image"))
                    .andExpect(jsonPath("$.latestHandwrittenSourceImageUrl")
                            .value("data:image/png;base64,abc"))
                    .andExpect(jsonPath("$.latestHandwrittenRecognizedText")
                            .value("handwritten source text"))
                    .andExpect(jsonPath("$.latestHandwrittenImportedAt")
                            .value("2026-04-12T10:30:00"));
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
