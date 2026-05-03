package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.controller.dto.assistant.AssistantConversationDetailResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantMessageResponse;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import com.personalenglishai.backend.controller.dto.assistant.SendAssistantMessageRequest;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.assistant.AssistantConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantController.class)
@AutoConfigureMockMvc(addFilters = false)
class AssistantControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssistantConversationService assistantConversationService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Test
    void sendMessage_acceptsMultipartFilesAndPassesThemToService() throws Exception {
        AssistantConversationDetailResponse response = new AssistantConversationDetailResponse(
                "conv-1",
                null,
                "作文图片评价",
                "请评价附件",
                false,
                false,
                LocalDateTime.of(2026, 5, 3, 10, 0),
                LocalDateTime.of(2026, 5, 3, 10, 1),
                List.of(new AssistantMessageResponse("msg-1", "assistant", "收到附件", "done", null)));

        when(assistantConversationService.sendMessageWithFiles(
                eq(1L),
                eq("conv-1"),
                any(SendAssistantMessageRequest.class),
                anyList(),
                eq("Bearer token")))
                .thenReturn(response);

        MockMultipartFile image = new MockMultipartFile(
                "files",
                "draft.png",
                "image/png",
                "fake-image".getBytes());

        mockMvc.perform(multipart("/api/assistant/conversations/conv-1/messages")
                        .file(image)
                        .param("message", "请评价这张作文图片")
                        .param("studyStage", "ielts")
                        .param("assistantMode", "exam")
                        .header("Authorization", "Bearer token")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").value("收到附件"));

        ArgumentCaptor<SendAssistantMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(SendAssistantMessageRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> filesCaptor = ArgumentCaptor.forClass(List.class);

        verify(assistantConversationService).sendMessageWithFiles(
                eq(1L),
                eq("conv-1"),
                requestCaptor.capture(),
                filesCaptor.capture(),
                eq("Bearer token"));

        assertThat(requestCaptor.getValue().getMessage()).isEqualTo("请评价这张作文图片");
        assertThat(requestCaptor.getValue().getStudyStage()).isEqualTo("ielts");
        assertThat(requestCaptor.getValue().getAssistantMode()).isEqualTo("exam");
        assertThat(filesCaptor.getValue()).hasSize(1);
        assertThat(filesCaptor.getValue().get(0).getOriginalFilename()).isEqualTo("draft.png");
    }

    @Test
    void sendAgentMessage_acceptsP0JsonRequestAndPassesItToService() throws Exception {
        AssistantConversationDetailResponse response = new AssistantConversationDetailResponse(
                "conv-1",
                null,
                "解释选中文本",
                "请解释",
                false,
                false,
                LocalDateTime.of(2026, 5, 3, 10, 0),
                LocalDateTime.of(2026, 5, 3, 10, 1),
                List.of(new AssistantMessageResponse("msg-1", "assistant", "解释结果", "done", null)));

        when(assistantConversationService.sendAgentMessage(
                eq(1L),
                eq("conv-1"),
                any(AssistantRequest.class),
                eq("Bearer token")))
                .thenReturn(response);

        mockMvc.perform(post("/api/assistant/conversations/conv-1/messages/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appConversationId": "conv-1",
                                  "clientMessageId": "client-1",
                                  "mode": "daily_explain",
                                  "intent": "explain",
                                  "scope": "selection_and_message",
                                  "message": { "text": "请解释" },
                                  "selection": {
                                    "text": "The rapid development of AI.",
                                    "source": "page_selection"
                                  }
                                }
                                """)
                        .header("Authorization", "Bearer token")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").value("解释结果"));

        ArgumentCaptor<AssistantRequest> requestCaptor = ArgumentCaptor.forClass(AssistantRequest.class);
        verify(assistantConversationService).sendAgentMessage(
                eq(1L),
                eq("conv-1"),
                requestCaptor.capture(),
                eq("Bearer token"));

        assertThat(requestCaptor.getValue().getClientMessageId()).isEqualTo("client-1");
        assertThat(requestCaptor.getValue().getIntent()).isEqualTo("explain");
        assertThat(requestCaptor.getValue().getMessage().getText()).isEqualTo("请解释");
        assertThat(requestCaptor.getValue().getSelection().getText()).isEqualTo("The rapid development of AI.");
    }
}
