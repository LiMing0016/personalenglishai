package com.personalenglishai.backend.service.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRequest;
import com.personalenglishai.backend.controller.dto.assistant.AssistantRunMetadataResponse;
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
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

class AssistantConversationServiceTest {
    private final AssistantProjectMapper projectMapper = mock(AssistantProjectMapper.class);
    private final AssistantConversationMapper conversationMapper = mock(AssistantConversationMapper.class);
    private final AssistantMessageMapper messageMapper = mock(AssistantMessageMapper.class);
    private final AssistantShareMapper shareMapper = mock(AssistantShareMapper.class);
    private final PythonAssistantClient pythonAssistantClient = mock(PythonAssistantClient.class);
    private final AssistantRequestValidator assistantRequestValidator = mock(AssistantRequestValidator.class);
    private final AgentDebugService agentDebugService = mock(AgentDebugService.class);
    private final LearningCaptureService learningCaptureService = mock(LearningCaptureService.class);
    private final AssistantUsageService assistantUsageService = mock(AssistantUsageService.class);

    @Test
    void sendAgentMessageChecksQuotaThenRecordsCompletedRunUsage() {
        AssistantConversationService service = service(new ObjectMapper());
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(0);
        when(messageMapper.selectByConversationUid("conv-history")).thenReturn(List.of());
        PythonAssistantClient.PythonAssistantReply reply = new PythonAssistantClient.PythonAssistantReply();
        reply.setReply("完成");
        AssistantRunMetadataResponse run = run("run-sync");
        reply.setRun(run);
        when(pythonAssistantClient.run(any(AssistantRequest.class), eq("Bearer token"))).thenReturn(reply);

        service.sendAgentMessage(4L, "conv-history", request("开始练习"), "Bearer token");

        var ordered = inOrder(assistantUsageService, pythonAssistantClient);
        ordered.verify(assistantUsageService).assertQuota(4L);
        ordered.verify(pythonAssistantClient).run(any(AssistantRequest.class), eq("Bearer token"));
        verify(assistantUsageService).record(4L, run);
    }

    @Test
    void writeAgentMessageStreamChecksQuotaAndRecordsRunCompletedUsageOnce() {
        AssistantConversationService service = service(new ObjectMapper());
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(0);
        when(pythonAssistantClient.streamRun(any(AssistantRequest.class), eq("Bearer token")))
                .thenReturn(Flux.just(
                        "{\"type\":\"message.completed\",\"content\":\"完成\",\"parts\":[]}",
                        "{\"type\":\"run.completed\",\"run\":{\"runId\":\"run-stream\",\"traceId\":\"trace-stream\",\"model\":\"gpt-5\",\"usage\":{\"inputTokens\":10,\"cachedInputTokens\":2,\"outputTokens\":5,\"totalTokens\":15,\"requests\":1}}}"));

        service.writeAgentMessageStream(
                4L,
                "conv-history",
                request("开始练习"),
                "Bearer token",
                new ByteArrayOutputStream());

        verify(assistantUsageService).assertQuota(4L);
        ArgumentCaptor<AssistantRunMetadataResponse> captor =
                ArgumentCaptor.forClass(AssistantRunMetadataResponse.class);
        verify(assistantUsageService).record(eq(4L), captor.capture());
        assertThat(captor.getValue().getRunId()).isEqualTo("run-stream");
        assertThat(captor.getValue().getUsage().getTotalTokens()).isEqualTo(15);
    }

    @Test
    void sendAgentMessage_persistsAndReturnsStructuredParts() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantConversationService service = service(objectMapper);
        List<AssistantMessage> storedMessages = new ArrayList<>();
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(0);
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0));
            return 1;
        }).when(messageMapper).insert(any(AssistantMessage.class));
        when(messageMapper.selectByConversationUid("conv-history")).thenAnswer(ignored -> storedMessages);

        ArrayNode parts = objectMapper.createArrayNode();
        parts.addObject()
                .put("type", "sentence_reorder")
                .put("version", 1)
                .putObject("data")
                .put("title", "重组成句");
        PythonAssistantClient.PythonAssistantReply reply = new PythonAssistantClient.PythonAssistantReply();
        reply.setReply("把词块排成正确句子。");
        reply.setParts(parts);
        when(pythonAssistantClient.run(any(AssistantRequest.class), eq("Bearer token"))).thenReturn(reply);

        var response = service.sendAgentMessage(4L, "conv-history", request("开始练习"), "Bearer token");

        assertThat(storedMessages).hasSize(2);
        assertThat(storedMessages.get(0).getPartsJson()).isNull();
        assertThat(storedMessages.get(1).getPartsJson()).isEqualTo(parts.toString());
        assertThat(response.getMessages().get(1).getParts()).isEqualTo(parts);
    }

    @Test
    void writeAgentMessageStream_forwardsAndPersistsCompletedParts() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantConversationService service = service(objectMapper);
        List<AssistantMessage> storedMessages = new ArrayList<>();
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(0);
        doAnswer(invocation -> {
            storedMessages.add(invocation.getArgument(0));
            return 1;
        }).when(messageMapper).insert(any(AssistantMessage.class));
        String completed = "{\"type\":\"message.completed\",\"content\":\"开始练习\",\"parts\":[{\"type\":\"sentence_reorder\",\"version\":1,\"data\":{\"title\":\"重组成句\"}}]}";
        when(pythonAssistantClient.streamRun(any(AssistantRequest.class), eq("Bearer token")))
                .thenReturn(Flux.just(completed));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeAgentMessageStream(4L, "conv-history", request("开始练习"), "Bearer token", output);

        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).contains(completed);
        assertThat(storedMessages).hasSize(2);
        assertThat(storedMessages.get(1).getPartsJson()).contains("sentence_reorder");
    }

    @Test
    void getConversation_ignoresMalformedHistoricalParts() {
        AssistantConversationService service = service(new ObjectMapper());
        AssistantMessage oldMessage = message("msg-old", "assistant", "旧内容", "done", 1);
        oldMessage.setPartsJson("not-json");
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectByConversationUid("conv-history")).thenReturn(List.of(oldMessage));

        var response = service.getConversation(4L, "conv-history");

        assertThat(response.getMessages().get(0).getContent()).isEqualTo("旧内容");
        assertThat(response.getMessages().get(0).getParts()).isNull();
    }

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
                learningCaptureService,
                assistantUsageService);
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
                learningCaptureService,
                assistantUsageService);
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

    @Test
    void sendAgentMessage_singleAgentRawLeavesConversationHistoryToSdkSession() {
        AssistantConversationService service = service(new ObjectMapper());
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(2);
        when(messageMapper.selectByConversationUid("conv-history"))
                .thenReturn(List.of(
                        message("msg-1", "user", "hive 是什么意思？", "done", 1),
                        message("msg-2", "assistant", "hive 可以表示蜂巢。", "done", 2)
                ));
        PythonAssistantClient.PythonAssistantReply reply = new PythonAssistantClient.PythonAssistantReply();
        reply.setReply("这里有两个例句。");
        when(pythonAssistantClient.run(any(AssistantRequest.class), eq("Bearer token"))).thenReturn(reply);
        AssistantRequest request = request("再来两个例句。");
        request.setAgentMode("single_agent_raw");

        service.sendAgentMessage(4L, "conv-history", request, "Bearer token");

        ArgumentCaptor<AssistantRequest> captor = ArgumentCaptor.forClass(AssistantRequest.class);
        org.mockito.Mockito.verify(pythonAssistantClient).run(captor.capture(), eq("Bearer token"));
        assertThat(captor.getValue().getConversationHistory()).isEmpty();
        assertThat(captor.getValue().getAgentMode()).isEqualTo("single_agent_raw");
    }

    @Test
    void writeAgentMessageStream_singleAgentRawLeavesConversationHistoryToSdkSession() {
        AssistantConversationService service = service(new ObjectMapper());
        when(conversationMapper.findOwnedActiveByUid(4L, "conv-history")).thenReturn(conversation());
        when(messageMapper.selectMaxSortOrder("conv-history")).thenReturn(2);
        when(messageMapper.selectByConversationUid("conv-history"))
                .thenReturn(List.of(
                        message("msg-1", "user", "hive 是什么意思？", "done", 1),
                        message("msg-2", "assistant", "hive 可以表示蜂巢。", "done", 2)
                ));
        when(pythonAssistantClient.streamRun(any(AssistantRequest.class), eq("Bearer token")))
                .thenReturn(Flux.just("{\"type\":\"message.completed\",\"content\":\"两个例句\",\"parts\":[]}"));
        AssistantRequest request = request("再来两个例句。");
        request.setAgentMode("single_agent_raw");

        service.writeAgentMessageStream(
                4L,
                "conv-history",
                request,
                "Bearer token",
                new ByteArrayOutputStream());

        ArgumentCaptor<AssistantRequest> captor = ArgumentCaptor.forClass(AssistantRequest.class);
        org.mockito.Mockito.verify(pythonAssistantClient).streamRun(captor.capture(), eq("Bearer token"));
        assertThat(captor.getValue().getConversationHistory()).isEmpty();
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

    private AssistantConversationService service(ObjectMapper objectMapper) {
        return new AssistantConversationService(
                projectMapper,
                conversationMapper,
                messageMapper,
                shareMapper,
                pythonAssistantClient,
                assistantRequestValidator,
                objectMapper,
                agentDebugService,
                learningCaptureService,
                assistantUsageService);
    }

    private AssistantRunMetadataResponse run(String runId) {
        AssistantRunMetadataResponse.Usage usage = new AssistantRunMetadataResponse.Usage();
        usage.setInputTokens(10);
        usage.setCachedInputTokens(2);
        usage.setOutputTokens(5);
        usage.setTotalTokens(15);
        usage.setRequests(1);
        AssistantRunMetadataResponse run = new AssistantRunMetadataResponse();
        run.setRunId(runId);
        run.setTraceId("trace-" + runId);
        run.setModel("gpt-5");
        run.setUsage(usage);
        return run;
    }

    private AssistantRequest request(String text) {
        AssistantRequest request = new AssistantRequest();
        request.setClientMessageId("client-1");
        request.setMode("daily_explain");
        request.setIntent("free_chat");
        AssistantRequest.Message message = new AssistantRequest.Message();
        message.setText(text);
        request.setMessage(message);
        return request;
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
