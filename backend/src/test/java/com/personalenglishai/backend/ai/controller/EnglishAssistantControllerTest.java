package com.personalenglishai.backend.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantChatRequest;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantChatResponse;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantService;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantUiAction;
import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnglishAssistantController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnglishAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnglishAssistantService englishAssistantService;

    @MockBean
    private AIRequestContextResolver requestContextResolver;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void chatShouldReturnStructuredEnglishAssistantResponse() throws Exception {
        RequestContext ctx = new RequestContext();
        ctx.setRequestId("trace-1");
        ctx.setUserId(1L);
        ctx.setTenantId("1");
        ctx.setWorkspaceId("default");

        EnglishAssistantChatResponse response = new EnglishAssistantChatResponse();
        response.setConversationId("conv-1");
        response.setResponseId("resp-1");
        response.setScope("current_draft");
        response.setTaskType("rewrite");
        response.setRefused(false);
        response.setRefusalReason(null);
        response.setUsedDraftContext(true);
        response.setMessage("Universities should continue to support these programs.");
        response.setActions(List.of(
                new EnglishAssistantUiAction(
                        "apply_rewrite",
                        "应用改写",
                        "Universities should continue to support these programs."
                )
        ));

        when(requestContextResolver.build(any())).thenReturn(ctx);
        when(englishAssistantService.chat(any(), any())).thenReturn(response);

        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-1");
        request.setMessage("帮我改写这句话");
        request.setUseDraftContext(true);
        request.setDraftText("The survey on students' main gains...");
        request.setSelectedText("Therefore, universities should continue...");
        request.setPreferredAction("rewrite");

        mockMvc.perform(post("/api/english-assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.responseId").value("resp-1"))
                .andExpect(jsonPath("$.scope").value("current_draft"))
                .andExpect(jsonPath("$.taskType").value("rewrite"))
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.usedDraftContext").value(true))
                .andExpect(jsonPath("$.message").value("Universities should continue to support these programs."))
                .andExpect(jsonPath("$.actions[0].type").value("apply_rewrite"))
                .andExpect(jsonPath("$.actions[0].payloadText").value("Universities should continue to support these programs."));
    }
}
