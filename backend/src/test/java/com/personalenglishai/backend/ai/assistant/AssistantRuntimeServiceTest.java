package com.personalenglishai.backend.ai.assistant;

import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.ContextRefs;
import com.personalenglishai.backend.ai.prompt.PromptAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AssistantRuntimeServiceTest {

    @Mock
    private AssistantOpenAiClient assistantOpenAiClient;

    @Mock
    private AssistantConversationStateService conversationStateService;

    @Mock
    private AssistantToolExecutor toolExecutor;

    @Test
    void chatShouldLoopThroughToolCallsAndPersistLatestResponseId() {
        AssistantRuntimeService service = new AssistantRuntimeService(
                assistantOpenAiClient,
                conversationStateService,
                toolExecutor
        );

        PromptAssembler.ChatPromptInput promptInput = new PromptAssembler.ChatPromptInput(
                "selection",
                "old sentence",
                "Draft body",
                "system prompt",
                "请润色刚才选中的句子"
        );

        AICommandRequest request = new AICommandRequest();
        request.setIntent("chat");
        request.setInstruction("请帮我改好这句话");
        request.setConstraints(Map.of(
                "conversationId", "conv-1",
                "selectedText", "old sentence",
                "includeDraft", true
        ));
        ContextRefs refs = new ContextRefs();
        refs.setDocId("doc-1");
        request.setContextRefs(refs);

        RequestContext ctx = new RequestContext();
        ctx.setRequestId("trace-1");
        ctx.setUserId(12L);
        ctx.setTenantId("12");
        ctx.setWorkspaceId("default");

        AIContext aiContext = AIContext.success("Draft body", "doc-1", true);

        when(conversationStateService.getLastResponseId("conv-1")).thenReturn("resp-prev");
        when(assistantOpenAiClient.createResponse(any()))
                .thenReturn(new AssistantOpenAiResponse(
                        "resp-tool",
                        null,
                        List.of(new AssistantToolCall("call-1", "get_score_summary", "{\"docId\":\"doc-1\"}"))
                ))
                .thenReturn(new AssistantOpenAiResponse(
                        "resp-final",
                        """
                                {
                                  "message": "这是本次作文的分数概览，我也给了你下一步建议。",
                                  "summary": ["总分稳定", "可以继续润色选中句子"],
                                  "actions": [
                                    {
                                      "type": "replace_selection",
                                      "label": "替换选中内容",
                                      "text": "A polished sentence."
                                    }
                                  ]
                                }
                                """,
                        List.of()
                ));
        when(toolExecutor.execute(eq("get_score_summary"), eq("{\"docId\":\"doc-1\"}"), eq(request), eq(ctx), eq(aiContext)))
                .thenReturn(new AssistantToolResult(
                        "{\"score\":88,\"band\":\"Band 4\"}",
                        "已读取最近一次评分结果"
                ));

        AssistantRunResult result = service.runChat(promptInput, request, ctx, aiContext, event -> {});

        assertThat(result.responseId()).isEqualTo("resp-final");
        assertThat(result.message()).isEqualTo("这是本次作文的分数概览，我也给了你下一步建议。");
        assertThat(result.summary()).containsExactly("总分稳定", "可以继续润色选中句子");
        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().get(0).type()).isEqualTo("replace_selection");
        assertThat(result.toolRuns())
                .extracting(AssistantToolRun::tool, AssistantToolRun::status, AssistantToolRun::summary)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "get_score_summary",
                        "completed",
                        "已读取最近一次评分结果"
                ));

        verify(conversationStateService).saveLastResponseId("conv-1", "resp-final");

        ArgumentCaptor<AssistantResponseRequest> requestCaptor = ArgumentCaptor.forClass(AssistantResponseRequest.class);
        verify(assistantOpenAiClient, org.mockito.Mockito.times(2)).createResponse(requestCaptor.capture());
        List<AssistantResponseRequest> sentRequests = requestCaptor.getAllValues();
        assertThat(sentRequests.get(0).previousResponseId()).isEqualTo("resp-prev");
        assertThat(sentRequests.get(1).previousResponseId()).isEqualTo("resp-tool");
        assertThat(sentRequests.get(1).toolOutputs())
                .singleElement()
                .extracting(AssistantToolOutput::callId, AssistantToolOutput::outputJson)
                .containsExactly("call-1", "{\"score\":88,\"band\":\"Band 4\"}");
    }

    @Test
    void chatShouldClearStoredResponseIdAndRetryWhenPreviousResponseIsMissing() {
        AssistantRuntimeService service = new AssistantRuntimeService(
                assistantOpenAiClient,
                conversationStateService,
                toolExecutor
        );

        PromptAssembler.ChatPromptInput promptInput = new PromptAssembler.ChatPromptInput(
                "auto",
                null,
                "Draft body",
                "system prompt",
                "继续分析这篇作文"
        );

        AICommandRequest request = new AICommandRequest();
        request.setIntent("chat");
        request.setInstruction("继续分析这篇作文");
        request.setConstraints(Map.of(
                "conversationId", "conv-retry"
        ));

        RequestContext ctx = new RequestContext();
        ctx.setRequestId("trace-retry");
        ctx.setUserId(12L);
        ctx.setTenantId("12");
        ctx.setWorkspaceId("default");

        AIContext aiContext = AIContext.success("Draft body", "doc-1", true);

        when(conversationStateService.getLastResponseId("conv-retry")).thenReturn("resp-stale");
        when(assistantOpenAiClient.createResponse(any()))
                .thenThrow(new RuntimeException("OpenAI assistant response failed status=400 code=previous_response_not_found param=previous_response_id"))
                .thenReturn(new AssistantOpenAiResponse(
                        "resp-fresh",
                        """
                                {
                                  "message": "我已经重新建立会话，并继续给出建议。",
                                  "summary": ["已丢弃失效的历史会话"],
                                  "actions": []
                                }
                                """,
                        List.of()
                ));

        AssistantRunResult result = service.runChat(promptInput, request, ctx, aiContext, event -> {});

        assertThat(result.responseId()).isEqualTo("resp-fresh");
        assertThat(result.message()).isEqualTo("我已经重新建立会话，并继续给出建议。");
        verify(conversationStateService).clear("conv-retry");
        verify(conversationStateService).saveLastResponseId("conv-retry", "resp-fresh");

        ArgumentCaptor<AssistantResponseRequest> requestCaptor = ArgumentCaptor.forClass(AssistantResponseRequest.class);
        verify(assistantOpenAiClient, org.mockito.Mockito.times(2)).createResponse(requestCaptor.capture());
        List<AssistantResponseRequest> sentRequests = requestCaptor.getAllValues();
        assertThat(sentRequests.get(0).previousResponseId()).isEqualTo("resp-stale");
        assertThat(sentRequests.get(1).previousResponseId()).isNull();
    }

    @Test
    void chatShouldNotClearConversationStateForOtherErrors() {
        AssistantRuntimeService service = new AssistantRuntimeService(
                assistantOpenAiClient,
                conversationStateService,
                toolExecutor
        );

        PromptAssembler.ChatPromptInput promptInput = new PromptAssembler.ChatPromptInput(
                "auto",
                null,
                "Draft body",
                "system prompt",
                "继续分析这篇作文"
        );

        AICommandRequest request = new AICommandRequest();
        request.setIntent("chat");
        request.setInstruction("继续分析这篇作文");
        request.setConstraints(Map.of(
                "conversationId", "conv-no-retry"
        ));

        RequestContext ctx = new RequestContext();
        ctx.setRequestId("trace-no-retry");
        ctx.setUserId(12L);
        ctx.setTenantId("12");
        ctx.setWorkspaceId("default");

        AIContext aiContext = AIContext.success("Draft body", "doc-1", true);

        when(conversationStateService.getLastResponseId("conv-no-retry")).thenReturn("resp-prev");
        when(assistantOpenAiClient.createResponse(any()))
                .thenThrow(new RuntimeException("OpenAI assistant response failed status=400 code=invalid_request_error"));

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> service.runChat(promptInput, request, ctx, aiContext, event -> {})
        );

        verify(conversationStateService, never()).clear("conv-no-retry");
        verify(conversationStateService, never()).saveLastResponseId(eq("conv-no-retry"), any());
    }
}
