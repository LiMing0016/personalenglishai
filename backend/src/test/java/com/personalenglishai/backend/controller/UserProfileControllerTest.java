package com.personalenglishai.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.entity.UserProfile;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.service.UserAbilityProfileService;
import com.personalenglishai.backend.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserAbilityProfileService userAbilityProfileService;

    @MockBean
    private EssayEvaluationMapper essayEvaluationMapper;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Nested
    @DisplayName("GET /api/users/me/profile")
    class GetProfile {

        @Test
        @DisplayName("returns user and profile fields")
        void getProfile_success() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setEmail("u1@example.com");
            user.setNickname("Catalina");

            when(userMapper.findById(1L)).thenReturn(user);
            when(userProfileService.getUserProfile(1L)).thenReturn(new UserProfile(1L, "cet4", 1));

            mockMvc.perform(get("/api/users/me/profile")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.message").value("OK"))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.email").value("u1@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("Catalina"))
                    .andExpect(jsonPath("$.data.studyStage").value("cet4"))
                    .andExpect(jsonPath("$.data.aiMode").value(1));
        }

        @Test
        @DisplayName("returns fallback data when user row does not exist")
        void getProfile_userMissing() throws Exception {
            when(userMapper.findById(2L)).thenReturn(null);
            when(userProfileService.getUserProfile(2L)).thenReturn(new UserProfile(2L, null, 0));

            mockMvc.perform(get("/api/users/me/profile")
                            .requestAttr("userId", 2L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(2))
                    .andExpect(jsonPath("$.data.email").doesNotExist())
                    .andExpect(jsonPath("$.data.nickname").doesNotExist())
                    .andExpect(jsonPath("$.data.aiMode").value(0));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me/profile/stage")
    class UpdateStage {

        @Test
        @DisplayName("updates stage and returns 204")
        void updateStage_success() throws Exception {
            mockMvc.perform(patch("/api/users/me/profile/stage")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", 1L)
                            .content(objectMapper.writeValueAsString(new StageBody("ielts"))))
                    .andExpect(status().isNoContent());

            verify(userProfileService).updateStudyStage(eq(1L), any());
        }
    }

    private record StageBody(String studyStage) {}
}

