package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateTaskResponse;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.service.writing.WritingChatService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingEvaluateTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WritingController.class)
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
    private EssayEvaluationMapper essayEvaluationMapper;

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
                    .andExpect(status().is4xxClientError());
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
                    .andExpect(status().is4xxClientError());
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
            when(writingEvaluateTaskService.getTask("eval-task-abc123")).thenReturn(mockResponse);

            mockMvc.perform(get("/api/writing/evaluate/tasks/eval-task-abc123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("succeeded"));
        }

        @Test
        @DisplayName("returns 404 when task not found")
        void getTask_notFound() throws Exception {
            when(writingEvaluateTaskService.getTask("nonexistent")).thenReturn(null);

            mockMvc.perform(get("/api/writing/evaluate/tasks/nonexistent"))
                    .andExpect(status().isNotFound());
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
    }

    private WritingEvaluateRequest buildRequest(String essay, String mode) {
        WritingEvaluateRequest req = new WritingEvaluateRequest();
        req.setEssay(essay);
        req.setMode(mode);
        return req;
    }
}
