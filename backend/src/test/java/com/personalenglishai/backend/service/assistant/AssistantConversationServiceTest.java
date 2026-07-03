package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.mapper.assistant.AssistantConversationMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantProjectMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantShareMapper;
import com.personalenglishai.backend.service.learning.LearningCaptureService;
import com.personalenglishai.backend.service.ops.AgentDebugService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantConversationServiceTest {
    private final AssistantProjectMapper projectMapper = mock(AssistantProjectMapper.class);
    private final AssistantConversationMapper conversationMapper = mock(AssistantConversationMapper.class);
    private final AssistantMessageMapper messageMapper = mock(AssistantMessageMapper.class);
    private final AssistantShareMapper shareMapper = mock(AssistantShareMapper.class);
    private final PythonAssistantClient pythonAssistantClient = mock(PythonAssistantClient.class);
    private final AssistantRequestValidator assistantRequestValidator = mock(AssistantRequestValidator.class);
    private final AgentDebugService agentDebugService = mock(AgentDebugService.class);
    private final LearningCaptureService learningCaptureService = mock(LearningCaptureService.class);

    @Test
    void sendAgentMessage_attachesRecentDoneMessagesAsConversationHistory() {
        AssistantConversationService service = new AssistantConversationService(
                projectMapper,
                conversationMapper,
                messageMapper,
                shareMapper,
                pythonAssistantClient,
                assistantRequestValidator,
                new ObjectMapper(),
                agentDebugService,
                learningCaptureService);
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(5);
        when(messageMapper.selectByConversationUid("conv-history"))
                .thenReturn(List.of(
                        message("msg-1", "user", "citation 是什么意思？", "done", 1),
                        message("msg-2", "assistant", "citation 表示引用、引证。", "done", 2),
                        message("msg-3", "user", "这条失败消息不应该带入。", "failed", 3),
                        message("msg-4", "assistant", "   ", "done", 4),
                        message("msg-5", "user", "那它和 reference 有什么区别？", "done", 5)
                ));
        PythonAssistantClient.PythonAssistantReply reply = new PythonAssistantClient.PythonAssistantReply();
        reply.setReply("二者的区别是...");
        when(pythonAssistantClient.run(any(AssistantRequest.class), eq("Bearer token"))).thenReturn(reply);

        AssistantRequest request = new AssistantRequest();
        request.setClientMessageId("client-1");
        request.setMode("daily_explain");
        request.setIntent("free_chat");
        AssistantRequest.Message message = new AssistantRequest.Message();
        message.setText("那它怎么造句？");
        request.setMessage(message);

        service.sendAgentMessage(4L, "conv-history", request, "Bearer token");

        ArgumentCaptor<AssistantRequest> requestCaptor = ArgumentCaptor.forClass(AssistantRequest.class);
        org.mockito.Mockito.verify(pythonAssistantClient).run(requestCaptor.capture(), eq("Bearer token"));
        List<AssistantRequest.ConversationHistoryMessage> history = requestCaptor.getValue().getConversationHistory();
        assertThat(history)
                .extracting(AssistantRequest.ConversationHistoryMessage::getRole,
                        AssistantRequest.ConversationHistoryMessage::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "citation 是什么意思？"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "citation 表示引用、引证。"),
                        org.assertj.core.groups.Tuple.tuple("user", "那它和 reference 有什么区别？")
                );
    }

    @Test
    void writeAgentMessageStream_attachesRecentDoneMessagesAsConversationHistory() {
        AssistantConversationService service = new AssistantConversationService(
                projectMapper,
                conversationMapper,
                messageMapper,
                shareMapper,
                pythonAssistantClient,
                assistantRequestValidator,
                new ObjectMapper(),
                agentDebugService,
                learningCaptureService);
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(5);
        when(messageMapper.selectByConversationUid("conv-history"))
                .thenReturn(List.of(
                        message("msg-1", "user", "citation 是什么意思？", "done", 1),
                        message("msg-2", "assistant", "citation 表示引用、引证。", "done", 2),
                        message("msg-3", "user", "这条失败消息不应该带入。", "failed", 3),
                        message("msg-4", "assistant", "   ", "done", 4),
                        message("msg-5", "user", "那它和 reference 有什么区别？", "done", 5)
                ));
        when(pythonAssistantClient.streamRun(any(AssistantRequest.class), eq("Bearer token")))
                .thenReturn(Flux.just("{\"type\":\"message.delta\",\"delta\":\"可以。\"}"));

        AssistantRequest request = new AssistantRequest();
        request.setClientMessageId("client-1");
        request.setMode("daily_explain");
        request.setIntent("free_chat");
        AssistantRequest.Message message = new AssistantRequest.Message();
        message.setText("那它怎么造句？");
        request.setMessage(message);

        service.writeAgentMessageStream(4L, "conv-history", request, "Bearer token", new ByteArrayOutputStream());

        ArgumentCaptor<AssistantRequest> requestCaptor = ArgumentCaptor.forClass(AssistantRequest.class);
        org.mockito.Mockito.verify(pythonAssistantClient).streamRun(requestCaptor.capture(), eq("Bearer token"));
        List<AssistantRequest.ConversationHistoryMessage> history = requestCaptor.getValue().getConversationHistory();
        assertThat(history)
                .extracting(AssistantRequest.ConversationHistoryMessage::getRole,
                        AssistantRequest.ConversationHistoryMessage::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "citation 是什么意思？"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "citation 表示引用、引证。"),
                        org.assertj.core.groups.Tuple.tuple("user", "那它和 reference 有什么区别？")
                );
    }

    private AssistantConversation conversation() {
        AssistantConversation conversation = new AssistantConversation();
        conversation.setConversationUid("conv-history");
        conversation.setUserId(4L);
        conversation.setTitle("新对话");
        conversation.setSummary("");
        conversation.setPinned(false);
        conversation.setCreatedAt(LocalDateTime.of(2026, 6, 30, 10, 0));
        conversation.setUpdatedAt(LocalDateTime.of(2026, 6, 30, 10, 0));
        return conversation;
    }

    private AssistantMessage message(String uid, String role, String content, String status, int sortOrder) {
        AssistantMessage message = new AssistantMessage();
        message.setMessageUid(uid);
        message.setConversationUid("conv-history");
        message.setUserId(4L);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        message.setSortOrder(sortOrder);
        return message;
    }
}
