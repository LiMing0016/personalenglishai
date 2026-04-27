package com.personalenglishai.backend.controller.document;

import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.entity.WritingTaskMetadata;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.writing.WritingTaskMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WritingTaskMetadataController.class)
@AutoConfigureMockMvc(addFilters = false)
class WritingTaskMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WritingTaskMetadataService writingTaskMetadataService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Test
    @DisplayName("POST /api/docs/{docId}/writing-task-metadata ensures metadata for current user")
    void ensureTaskMetadata() throws Exception {
        WritingTaskMetadata metadata = new WritingTaskMetadata();
        metadata.setDocumentPublicId("doc-1");
        metadata.setStudyStage("ielts");
        metadata.setTaskType("argumentative");
        metadata.setCentralTask("讨论 online learning 是否优于 traditional learning，并给出清晰立场。");
        metadata.setMustAnswerPointsJson("[\"明确回答题目核心问题\"]");
        metadata.setWritingFocusJson("[\"观点清楚\"]");
        metadata.setRiskPointsJson("[\"避免偏题\"]");
        metadata.setRecommendedStructureJson("{\"intro\":\"背景 + 观点\"}");
        metadata.setRubricFocusJson("[\"task_response\"]");
        metadata.setMetadataVersion("writing-task-metadata@test-v1");
        metadata.setRubricSource("writing-coach-stage-policy@test-v1");

        when(writingTaskMetadataService.ensureForDocument("1", "default", "doc-1", 1L)).thenReturn(metadata);

        mockMvc.perform(post("/api/docs/doc-1/writing-task-metadata").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.studyStage").value("ielts"))
                .andExpect(jsonPath("$.taskType").value("argumentative"))
                .andExpect(jsonPath("$.centralTask").value(metadata.getCentralTask()))
                .andExpect(jsonPath("$.mustAnswerPoints[0]").value("明确回答题目核心问题"))
                .andExpect(jsonPath("$.recommendedStructure.intro").value("背景 + 观点"))
                .andExpect(jsonPath("$.metadataVersion").value("writing-task-metadata@test-v1"));

        verify(writingTaskMetadataService).ensureForDocument("1", "default", "doc-1", 1L);
    }
}
