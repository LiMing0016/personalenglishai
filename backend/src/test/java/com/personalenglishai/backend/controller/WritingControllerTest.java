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
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.impl.WritingSuggestionsService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
    private GrammarCheckService grammarCheckService;

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

    private WritingEvaluateRequest buildRequest(String essay, String mode) {
        WritingEvaluateRequest req = new WritingEvaluateRequest();
        req.setEssay(essay);
        req.setMode(mode);
        return req;
    }
}


