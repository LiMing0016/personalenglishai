package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.learning.LearningDeepseekCleaningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminLearningCaptureController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminLearningCaptureControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuthorizationService adminAuthorizationService;

    @MockBean
    private LearningDeepseekCleaningService cleaningService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Test
    void processPendingRunsRequiresAdminAndReturnsProcessedCount() throws Exception {
        when(cleaningService.processPendingRuns(5)).thenReturn(3);

        mockMvc.perform(post("/api/admin/learning-capture/deepseek/pending")
                        .param("limit", "5")
                        .requestAttr("userId", 99L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3));

        verify(adminAuthorizationService).requireAdmin(99L);
        verify(cleaningService).processPendingRuns(5);
    }

    @Test
    void processMessageRequiresAdminAndReturnsProcessedFlag() throws Exception {
        when(cleaningService.processMessage("msg-1")).thenReturn(true);

        mockMvc.perform(post("/api/admin/learning-capture/deepseek/messages/msg-1")
                        .requestAttr("userId", 99L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true));

        verify(adminAuthorizationService).requireAdmin(99L);
        verify(cleaningService).processMessage("msg-1");
    }

    @Test
    void processUserDayRequiresAdminAndReturnsProcessedCount() throws Exception {
        when(cleaningService.processPendingRunsForUserDay(1L, java.time.LocalDate.of(2026, 5, 17), 20)).thenReturn(8);

        mockMvc.perform(post("/api/admin/learning-capture/deepseek/users/1/days/2026-05-17")
                        .param("limit", "20")
                        .requestAttr("userId", 99L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(8));

        verify(adminAuthorizationService).requireAdmin(99L);
        verify(cleaningService).processPendingRunsForUserDay(1L, java.time.LocalDate.of(2026, 5, 17), 20);
    }
}
