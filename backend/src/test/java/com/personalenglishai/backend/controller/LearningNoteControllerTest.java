package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.dto.learning.LearningCanvasOrganizeResponse;
import com.personalenglishai.backend.dto.learning.LearningNoteResponse;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.learning.LearningCanvasOrganizeService;
import com.personalenglishai.backend.service.learning.LearningNoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LearningNoteController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("LearningNoteController")
class LearningNoteControllerTest {

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockBean
    private LearningNoteService learningNoteService;

    @MockBean
    private LearningCanvasOrganizeService organizeService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Test
    @DisplayName("rejects create without login")
    void rejectsCreateWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/learning-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"vocabulary","title":"nuanced","contentMarkdown":"# nuanced"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401000"));
    }

    @Test
    @DisplayName("creates learning note for logged in user")
    void createsLearningNote() throws Exception {
        LearningNoteResponse response = new LearningNoteResponse();
        response.setNoteUid("note-1");
        response.setType("vocabulary");
        response.setTitle("nuanced");
        response.setContentMarkdown("# nuanced");

        when(learningNoteService.create(eq(7L), any())).thenReturn(response);

        mockMvc.perform(post("/api/learning-notes")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"vocabulary","title":"nuanced","contentMarkdown":"# nuanced"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.noteUid").value("note-1"))
                .andExpect(jsonPath("$.data.title").value("nuanced"));
    }

    @Test
    @DisplayName("organizes selected text into markdown candidate")
    void organizesSelectedText() throws Exception {
        LearningCanvasOrganizeResponse response = new LearningCanvasOrganizeResponse();
        response.setCandidateMarkdown("# nuanced\n\n**中文释义：** 细致入微的");

        when(organizeService.organize(any())).thenReturn(response);

        mockMvc.perform(post("/api/learning-notes/organize")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"vocabulary","title":"nuanced","selectedText":"nuanced","mode":"create"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.candidateMarkdown").value("# nuanced\n\n**中文释义：** 细致入微的"));
    }
}
