package com.personalenglishai.backend.ai.handler.impl;

import com.personalenglishai.backend.ai.assistant.AssistantAction;
import com.personalenglishai.backend.ai.assistant.AssistantRunResult;
import com.personalenglishai.backend.ai.assistant.AssistantRuntimeService;
import com.personalenglishai.backend.ai.assistant.AssistantToolRun;
import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.dto.ContextRefs;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor;
import com.personalenglishai.backend.ai.prompt.PromptAssembler;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.service.UserAbilityProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHandlerTest {

    @Mock
    private PromptAssembler promptAssembler;

    @Mock
    private ConversationContextProcessor conversationContextProcessor;

    @Mock
    private UserAbilityProfileService userAbilityProfileService;

    @Mock
    private AssistantRuntimeService assistantRuntimeService;

    @Test
    void handleShouldReturnStructuredAssistantPayload() {
        ChatHandler handler = new ChatHandler(
                promptAssembler,
                conversationContextProcessor,
                userAbilityProfileService,
                assistantRuntimeService,
                false
        );

        AICommandRequest request = new AICommandRequest();
        request.setIntent("chat");
        request.setInstruction("帮我润色一下");
        request.setConstraints(Map.of("conversationId", "conv-1"));
        ContextRefs refs = new ContextRefs();
        refs.setDocId("doc-1");
        request.setContextRefs(refs);

        RequestContext ctx = new RequestContext();
        ctx.setRequestId("trace-1");
        ctx.setUserId(1L);
        ctx.setTenantId("1");
        ctx.setWorkspaceId("default");

        AIContext aiContext = AIContext.success("Draft body", "doc-1", true);
        PromptAssembler.ChatPromptInput promptInput = new PromptAssembler.ChatPromptInput(
                "selection",
                "old sentence",
                "Draft body",
                "system prompt",
                "请润色选中句子"
        );

        when(userAbilityProfileService.getByUserId(1L)).thenReturn(new UserAbilityProfile());
        when(promptAssembler.buildChatPromptInput(eq(request), eq(aiContext), any(UserAbilityProfile.class), eq(1L), eq("trace-1")))
                .thenReturn(promptInput);
        when(assistantRuntimeService.runChat(eq(promptInput), eq(request), eq(ctx), eq(aiContext), any()))
                .thenReturn(new AssistantRunResult(
                        "resp-1",
                        "我已经给你更自然的版本了。",
                        List.of("句子更自然", "保留原意"),
                        List.of(new AssistantAction("replace_selection", "替换选中内容", "A polished sentence.", null)),
                        List.of(new AssistantToolRun("polish_selection", "completed", "已生成润色版本"))
                ));

        AICommandResponse response = handler.handle(request, ctx, aiContext);

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("我已经给你更自然的版本了。");
        assertThat(response.getResponseId()).isEqualTo("resp-1");
        assertThat(response.getActions()).hasSize(1);
        assertThat(response.getToolRuns()).hasSize(1);
        assertThat(response.getResult().getApply()).isEqualTo("我已经给你更自然的版本了。");
        assertThat(response.getResult().getExplain()).containsExactly("句子更自然", "保留原意");
        assertThat(response.getFinalResult().getContent()).isEqualTo("我已经给你更自然的版本了。");

        verify(assistantRuntimeService).runChat(eq(promptInput), eq(request), eq(ctx), eq(aiContext), any());
    }
}
