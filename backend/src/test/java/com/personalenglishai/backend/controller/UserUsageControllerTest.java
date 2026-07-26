package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.service.auth.AuthService;
import com.personalenglishai.backend.service.subscription.AiUsageActivityService;
import com.personalenglishai.backend.service.subscription.dto.AiUsageActivityResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserUsageControllerTest {

    @Test
    void returnsAuthenticatedUsersAiUsageActivity() throws Exception {
        AiUsageActivityService activityService = mock(AiUsageActivityService.class);
        UserController controller = new UserController(mock(AuthService.class), activityService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        AiUsageActivityResponse response = new AiUsageActivityResponse(
                "ai_tokens",
                "token",
                "Asia/Shanghai",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 26),
                30L,
                List.of(new AiUsageActivityResponse.DayBucket(
                        LocalDate.of(2026, 7, 26),
                        30L,
                        Map.of("assistant", 30L))));
        when(activityService.getActivity(
                7L,
                "ai_tokens",
                "day",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 26),
                "Asia/Shanghai"))
                .thenReturn(response);

        mockMvc.perform(get("/api/users/me/usage")
                        .requestAttr("userId", 7L)
                        .param("metric", "ai_tokens")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-26")
                        .param("granularity", "day")
                        .param("timezone", "Asia/Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metric").value("ai_tokens"))
                .andExpect(jsonPath("$.data.unit").value("token"))
                .andExpect(jsonPath("$.data.buckets[0].byProduct.assistant").value(30));
    }
}
