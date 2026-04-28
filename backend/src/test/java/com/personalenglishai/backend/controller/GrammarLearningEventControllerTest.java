package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchRequest;
import com.personalenglishai.backend.dto.learning.GrammarLearningEventBatchResult;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.learning.GrammarLearningEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GrammarLearningEventController.class)
@AutoConfigureMockMvc(addFilters = false)
class GrammarLearningEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GrammarLearningEventService grammarLearningEventService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Test
    @DisplayName("POST /api/learning-events/grammar/batch accepts grammar events for current user")
    void batch_acceptsGrammarEventsForCurrentUser() throws Exception {
        when(grammarLearningEventService.acceptBatch(eq(123L), any(GrammarLearningEventBatchRequest.class)))
                .thenReturn(new GrammarLearningEventBatchResult(
                        true,
                        1,
                        1,
                        0,
                        List.of(
                                new GrammarLearningEventBatchResult.EventResult("evt_grammar_sample", "accepted"),
                                new GrammarLearningEventBatchResult.EventResult("evt_grammar_error", "deduplicated")
                        )
                ));

        Map<String, Object> request = Map.of(
                "userId", 123,
                "conversationId", "conv-1",
                "messageId", "msg-1",
                "events", List.of(
                        Map.of(
                                "eventId", "evt_grammar_sample",
                                "eventType", "grammar_sample_checked",
                                "occurredAt", "2026-04-25T10:05:00.000Z",
                                "contentOrigin", "user_submission",
                                "profileEligible", true,
                                "confidence", 0.95,
                                "payload", Map.of(
                                        "sentenceHash", "sha256:abc",
                                        "hasGrammarIssue", true
                                )
                        )
                )
        );

        mockMvc.perform(post("/api/learning-events/grammar/batch")
                        .requestAttr("userId", 123L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.acceptedCount").value(1))
                .andExpect(jsonPath("$.data.deduplicatedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].eventId").value("evt_grammar_sample"));

        verify(grammarLearningEventService).acceptBatch(eq(123L), any(GrammarLearningEventBatchRequest.class));
    }
}
