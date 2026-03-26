package com.personalenglishai.backend.ai.englishassistant;

import com.personalenglishai.backend.ai.context.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnglishAssistantServiceTest {

    @Mock
    private EnglishAssistantScopeRouter scopeRouter;

    @Mock
    private EnglishAssistantAnswerService answerService;

    @Mock
    private EnglishAssistantConversationStore conversationStore;

    @Mock
    private EnglishAssistantRubricContextService rubricContextService;

    @Mock
    private EnglishAssistantContextAssembler contextAssembler;

    @Mock
    private EnglishAssistantSummaryService summaryService;

    private EnglishAssistantService service;

    @BeforeEach
    void setUp() {
        service = new EnglishAssistantService(
                scopeRouter,
                answerService,
                conversationStore,
                rubricContextService,
                contextAssembler,
                summaryService
        );
        lenient().when(contextAssembler.assemble(any(), any())).thenAnswer(invocation -> {
            EnglishAssistantAnswerRequest req = invocation.getArgument(0);
            return new EnglishAssistantContextBundle(
                    req.getAssignmentText(),
                    req.getSelectedText(),
                    req.getDraftText(),
                    req.getAssistantOutputText(),
                    req.getRubricSummary(),
                    req.getRecentTurnsText(),
                    req.getSummaryText(),
                    "full",
                    false,
                    false,
                    100
            );
        });
    }

    @Test
    void chatShouldRefuseOffTopicWithoutCallingAnswerService() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-1");
        request.setMessage("1+1 等于几");
        request.setUseDraftContext(false);

        RequestContext ctx = requestContext("trace-1", 1L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "off_topic",
                        "ask",
                        false,
                        "数学问题不在处理范围内"
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        assertThat(response.getConversationId()).isEqualTo("conv-1");
        assertThat(response.getScope()).isEqualTo("off_topic");
        assertThat(response.getTaskType()).isEqualTo("ask");
        assertThat(response.getRefused()).isTrue();
        assertThat(response.getRefusalReason()).isEqualTo("数学问题不在处理范围内");
        assertThat(response.getUsedDraftContext()).isFalse();
        assertThat(response.getMessage()).contains("英语学习和英语写作");

        verify(answerService, never()).answer(any());
    }

    @Test
    void chatShouldResetDraftThreadWhenDraftHashChanges() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-draft");
        request.setMessage("帮我解释第二段最后一句为什么别扭");
        request.setUseDraftContext(true);
        request.setAssignmentText("Write an essay about school life.");
        request.setSelectedText("Therefore, universities should continue...");
        request.setDraftText("The survey on students' main gains...");
        request.setPreferredAction("ask");

        RequestContext ctx = requestContext("trace-draft", 9L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "current_draft",
                        "explain",
                        true,
                        null
                ));
        when(conversationStore.getState("conv-draft"))
                .thenReturn(new EnglishAssistantConversationState(
                        "resp-general-old",
                        "resp-draft-old",
                        "old-draft-hash",
                        null,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-draft-new",
                        "这句话别扭，主要是 because 后面的逻辑衔接不自然。",
                        321,
                        128
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getScope()).isEqualTo("current_draft");
        assertThat(sent.getTaskType()).isEqualTo("explain");
        assertThat(sent.getPreviousResponseId()).isNull();
        assertThat(sent.getUseDraftContext()).isTrue();
        assertThat(sent.getAssignmentText()).isEqualTo("Write an essay about school life.");
        assertThat(sent.getSelectedText()).isEqualTo("Therefore, universities should continue...");
        assertThat(sent.getDraftText()).isEqualTo("The survey on students' main gains...");

        assertThat(response.getScope()).isEqualTo("current_draft");
        assertThat(response.getTaskType()).isEqualTo("explain");
        assertThat(response.getRefused()).isFalse();
        assertThat(response.getUsedDraftContext()).isTrue();
        assertThat(response.getResponseId()).isEqualTo("resp-draft-new");

        verify(conversationStore).saveDraftState(eq("conv-draft"), eq("resp-draft-new"), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void chatShouldInjectDynamicRubricForDraftScope() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-postgrad");
        request.setMessage("按当前标准看看这段作文");
        request.setUseDraftContext(true);
        request.setAssignmentText("Write an essay.");
        request.setSelectedText("Students should keep learning.");
        request.setDraftText("Students should keep learning and practicing.");
        request.setPreferredAction("evaluate");
        request.setStudyStage("postgrad");
        request.setWritingMode("exam");

        RequestContext ctx = requestContext("trace-postgrad", 10L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "current_draft",
                        "evaluate",
                        true,
                        null
                ));
        when(rubricContextService.resolve("postgrad", "exam"))
                .thenReturn(new EnglishAssistantRubricContext(
                        "postgrad-exam-v1",
                        "rubric summary"
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-postgrad",
                        "这篇作文任务完成度一般。",
                        220,
                        80
                ));

        service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getRubricKey()).isEqualTo("postgrad-exam-v1");
        assertThat(sent.getRubricSummary()).isEqualTo("rubric summary");
    }

    @Test
    void chatShouldNotInjectRubricForGeneralEnglishScope() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-general");
        request.setMessage("distinguish 和 differentiate 有什么区别");
        request.setUseDraftContext(false);
        request.setStudyStage("postgrad");
        request.setWritingMode("exam");

        RequestContext ctx = requestContext("trace-general", 2L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "english_general",
                        "ask",
                        false,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-general",
                        "两者都表示区分，但 distinguish 更常见。",
                        180,
                        50
                ));

        service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getRubricKey()).isNull();
        assertThat(sent.getRubricSummary()).isNull();
        verify(rubricContextService, never()).resolve(any(), any());
    }

    @Test
    void chatShouldPassGeneralPreviousResponseIdIntoRouter() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-general");
        request.setMessage("关于大学生就业的");
        request.setUseDraftContext(false);
        request.setPreferredAction("ask");

        RequestContext ctx = requestContext("trace-general-followup", 3L);

        when(conversationStore.getState("conv-general"))
                .thenReturn(new EnglishAssistantConversationState(
                        "resp-general-old",
                        null,
                        null,
                        null,
                        null
                ));
        when(scopeRouter.route(eq(request), eq(ctx), eq("resp-general-old"), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "english_general",
                        "generate",
                        false,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-general-new",
                        "请告诉我你希望从哪个角度写大学生就业。",
                        150,
                        0
                ));

        service.chat(request, ctx);

        verify(scopeRouter).route(eq(request), eq(ctx), eq("resp-general-old"), eq(false));
    }

    @Test
    void chatShouldAllowSessionMetaQuestions() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-session-meta");
        request.setMessage("你能记住我的上下文吗");
        request.setUseDraftContext(false);
        request.setPreferredAction("ask");

        RequestContext ctx = requestContext("trace-session-meta", 5L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "session_meta",
                        "ask",
                        false,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-session-meta",
                        "可以。我会结合当前会话继续理解你的问题。",
                        90,
                        0
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        assertThat(response.getRefused()).isFalse();
        assertThat(response.getScope()).isEqualTo("session_meta");
        verify(answerService).answer(any());
    }

    @Test
    void chatShouldRefuseSensitiveTopicsWithoutCallingAnswerService() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-sensitive");
        request.setMessage("你支持哪个政党");
        request.setUseDraftContext(false);

        RequestContext ctx = requestContext("trace-sensitive", 6L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "sensitive_refuse",
                        "ask",
                        false,
                        "politics_sensitive"
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        assertThat(response.getRefused()).isTrue();
        assertThat(response.getScope()).isEqualTo("sensitive_refuse");
        assertThat(response.getRefusalReason()).isEqualTo("politics_sensitive");
        verify(answerService, never()).answer(any());
    }

    @Test
    void chatShouldOverrideOffTopicRouteForStrongDraftReference() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-word-count");
        request.setMessage("这篇作文有多少字");
        request.setUseDraftContext(true);
        request.setDraftText("This is a draft about graduate employment opportunities.");
        request.setAssignmentText("Write an essay about employment.");
        request.setPreferredAction("ask");

        RequestContext ctx = requestContext("trace-word-count", 4L);

        when(scopeRouter.route(eq(request), eq(ctx), eq((String) null), eq(false)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "off_topic",
                        "ask",
                        false,
                        "router_misclassified"
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-word-count",
                        "这篇作文大约有 8 个单词。",
                        120,
                        0
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getScope()).isEqualTo("current_draft");
        assertThat(sent.getUseDraftContext()).isTrue();
        assertThat(response.getRefused()).isFalse();
        assertThat(response.getScope()).isEqualTo("current_draft");
    }

    @Test
    void chatShouldUseLastAssistantOutputForFollowUpReference() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-artifact");
        request.setMessage("翻译一下最后一段");
        request.setUseDraftContext(false);
        request.setPreferredAction("translate");

        RequestContext ctx = requestContext("trace-artifact", 7L);

        when(conversationStore.getState("conv-artifact"))
                .thenReturn(new EnglishAssistantConversationState(
                        "resp-general-old",
                        null,
                        null,
                        "Title: Preparing for Graduate School Entrance Exams\n\n"
                                + "In conclusion, preparing for graduate school entrance exams is a challenging yet rewarding endeavor.",
                        null,
                        "general",
                        "resp-general-old",
                        "Title: Preparing for Graduate School Entrance Exams\n\n"
                                + "In conclusion, preparing for graduate school entrance exams is a challenging yet rewarding endeavor.",
                        "generate",
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        0,
                        0,
                        0,
                        0
                ));
        when(scopeRouter.route(eq(request), eq(ctx), eq("resp-general-old"), eq(true)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "assistant_output",
                        "translate",
                        false,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-artifact-new",
                        "最后一段的中文翻译。",
                        140,
                        0
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getScope()).isEqualTo("assistant_output");
        assertThat(sent.getUseDraftContext()).isFalse();
        assertThat(sent.getAssistantOutputText()).contains("In conclusion");
        assertThat(sent.getPreviousResponseId()).isEqualTo("resp-general-old");
        assertThat(response.getRefused()).isFalse();
        assertThat(response.getScope()).isEqualTo("assistant_output");
    }

    @Test
    void chatShouldOverrideCurrentDraftRouteWhenAssistantOutputReferenceExists() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-artifact-override");
        request.setMessage("翻译一下最后一段");
        request.setUseDraftContext(false);
        request.setPreferredAction("translate");

        RequestContext ctx = requestContext("trace-artifact-override", 8L);

        when(conversationStore.getState("conv-artifact-override"))
                .thenReturn(new EnglishAssistantConversationState(
                        "resp-general-old",
                        null,
                        null,
                        "Title: Preparing for Graduate School Entrance Exams\n\n"
                                + "In conclusion, preparing for graduate school entrance exams is a challenging yet rewarding endeavor.",
                        null,
                        "general",
                        "resp-general-old",
                        "Title: Preparing for Graduate School Entrance Exams\n\n"
                                + "In conclusion, preparing for graduate school entrance exams is a challenging yet rewarding endeavor.",
                        "generate",
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        0,
                        0,
                        0,
                        0
                ));
        when(scopeRouter.route(eq(request), eq(ctx), eq("resp-general-old"), eq(true)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "current_draft",
                        "translate",
                        true,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-artifact-override-new",
                        "最后一段的中文翻译。",
                        140,
                        0
                ));

        EnglishAssistantChatResponse response = service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getScope()).isEqualTo("assistant_output");
        assertThat(sent.getAssistantOutputText()).contains("In conclusion");
        assertThat(response.getRefused()).isFalse();
        assertThat(response.getScope()).isEqualTo("assistant_output");
    }

    @Test
    void chatShouldPreferLastReusableDraftArtifactOverGeneralOutput() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-artifact-draft");
        request.setMessage("翻译一下最后一段");
        request.setUseDraftContext(false);
        request.setPreferredAction("translate");

        RequestContext ctx = requestContext("trace-artifact-draft", 11L);

        when(conversationStore.getState("conv-artifact-draft"))
                .thenReturn(new EnglishAssistantConversationState(
                        "resp-general-old",
                        "resp-draft-old",
                        "hash-1",
                        "普通英语回答",
                        "Draft title\n\nParagraph one.\n\nFinal paragraph from draft artifact.",
                        "draft",
                        "resp-draft-old",
                        "Draft title\n\nParagraph one.\n\nFinal paragraph from draft artifact.",
                        "generate",
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null,
                        1,
                        1,
                        0,
                        0
                ));
        when(scopeRouter.route(eq(request), eq(ctx), eq("resp-draft-old"), eq(true)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "assistant_output",
                        "translate",
                        false,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-artifact-draft-new",
                        "这里是翻译。",
                        120,
                        0
                ));

        service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getPreviousResponseId()).isEqualTo("resp-draft-old");
        assertThat(sent.getAssistantOutputText()).contains("Final paragraph from draft artifact.");
    }

    @Test
    void chatShouldUseArtifactPointerWhenArtifactIsNotCurrentDraftHead() {
        EnglishAssistantChatRequest request = new EnglishAssistantChatRequest();
        request.setConversationId("conv-artifact-detached");
        request.setMessage("翻译一下最后一段");
        request.setUseDraftContext(false);
        request.setPreferredAction("translate");

        RequestContext ctx = requestContext("trace-artifact-detached", 12L);

        when(conversationStore.getState("conv-artifact-detached"))
                .thenReturn(new EnglishAssistantConversationState(
                        "resp-general-old",
                        "resp-draft-followup",
                        "hash-2",
                        "普通问答",
                        "后续解释，不是要翻译的范文全文。",
                        "draft",
                        "resp-draft-artifact",
                        "Draft title\n\nParagraph one.\n\nFinal paragraph from earlier draft artifact.",
                        "generate",
                        java.util.List.of(),
                        java.util.List.of(new EnglishAssistantTurn("上一轮问题", "后续解释", "current_draft", "explain")),
                        null,
                        null,
                        1,
                        2,
                        0,
                        0
                ));
        when(scopeRouter.route(eq(request), eq(ctx), eq("resp-draft-artifact"), eq(true)))
                .thenReturn(new EnglishAssistantRouterResult(
                        "assistant_output",
                        "translate",
                        false,
                        null
                ));
        when(answerService.answer(any()))
                .thenReturn(new EnglishAssistantAnswerResult(
                        "resp-artifact-detached-new",
                        "这是最后一段的正式中文翻译结果，适合继续引用和应用。",
                        100,
                        0
                ));

        service.chat(request, ctx);

        ArgumentCaptor<EnglishAssistantAnswerRequest> answerCaptor =
                ArgumentCaptor.forClass(EnglishAssistantAnswerRequest.class);
        verify(answerService).answer(answerCaptor.capture());
        EnglishAssistantAnswerRequest sent = answerCaptor.getValue();
        assertThat(sent.getPreviousResponseId()).isEqualTo("resp-draft-artifact");
        assertThat(sent.getArtifactChain()).isEqualTo("draft");
        assertThat(sent.getAssistantOutputText()).contains("Final paragraph from earlier draft artifact.");

        verify(conversationStore).saveDraftState(
                eq("conv-artifact-detached"),
                eq("resp-artifact-detached-new"),
                eq("hash-2"),
                eq("这是最后一段的正式中文翻译结果，适合继续引用和应用。"),
                eq("这是最后一段的正式中文翻译结果，适合继续引用和应用。"),
                eq("translate"),
                any(),
                any(),
                anyInt(),
                anyInt()
        );
    }

    private RequestContext requestContext(String traceId, Long userId) {
        RequestContext ctx = new RequestContext();
        ctx.setRequestId(traceId);
        ctx.setUserId(userId);
        ctx.setTenantId(String.valueOf(userId));
        ctx.setWorkspaceId("default");
        return ctx;
    }
}
