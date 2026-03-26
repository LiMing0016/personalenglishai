package com.personalenglishai.backend.ai.englishassistant;

import com.personalenglishai.backend.ai.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Service
public class EnglishAssistantService {

    private static final Logger log = LoggerFactory.getLogger(EnglishAssistantService.class);
    private static final String OFF_TOPIC_MESSAGE = "我主要回答英语学习和英语写作相关问题。你可以问我单词、语法、翻译、改写，或当前作文方面的问题。";
    private static final String SENSITIVE_REFUSE_MESSAGE = "我不能帮助处理政治、色情或其他敏感高风险话题。你可以继续问我英语学习、英语写作或当前作文相关问题。";
    private static final String DRAFT_CONTEXT_REQUIRED_MESSAGE = "这个问题需要结合你当前作文来回答。请先开启“引用作文”，再继续提问。";

    private final EnglishAssistantScopeRouter scopeRouter;
    private final EnglishAssistantAnswerService answerService;
    private final EnglishAssistantConversationStore conversationStore;
    private final EnglishAssistantRubricContextService rubricContextService;
    private final EnglishAssistantContextAssembler contextAssembler;
    private final EnglishAssistantSummaryService summaryService;

    public EnglishAssistantService(EnglishAssistantScopeRouter scopeRouter,
                                   EnglishAssistantAnswerService answerService,
                                   EnglishAssistantConversationStore conversationStore,
                                   EnglishAssistantRubricContextService rubricContextService,
                                   EnglishAssistantContextAssembler contextAssembler,
                                   EnglishAssistantSummaryService summaryService) {
        this.scopeRouter = scopeRouter;
        this.answerService = answerService;
        this.conversationStore = conversationStore;
        this.rubricContextService = rubricContextService;
        this.contextAssembler = contextAssembler;
        this.summaryService = summaryService;
    }

    public EnglishAssistantChatResponse chat(EnglishAssistantChatRequest request, RequestContext ctx) {
        EnglishAssistantConversationState state = conversationStore.getState(request.getConversationId());
        String routerPreviousResponseId = resolveRouterPreviousResponseId(request, state);
        EnglishAssistantRouterResult route = applyRouteOverrides(
                scopeRouter.route(request, ctx, routerPreviousResponseId, hasReusableArtifact(state)),
                request,
                state
        );
        if (isSensitiveRefuse(route)) {
            return refused(request.getConversationId(), route, SENSITIVE_REFUSE_MESSAGE);
        }
        if (isOffTopic(route)) {
            return refused(request.getConversationId(), route, OFF_TOPIC_MESSAGE);
        }
        if (needsDraftButDisabled(route, request)) {
            return refused(request.getConversationId(), route, DRAFT_CONTEXT_REQUIRED_MESSAGE);
        }

        String draftHash = shouldUseDraftContext(route, request) ? hashDraft(request.getDraftText()) : null;
        if (isDraftScope(route) && state != null && state.lastDraftHash() != null && draftHash != null
                && !state.lastDraftHash().equals(draftHash)) {
            conversationStore.clearDraftState(request.getConversationId());
            state = state.withoutDraftChain();
        }
        String previousResponseId = resolvePreviousResponseId(route, state, draftHash);

        EnglishAssistantAnswerRequest answerRequest = buildAnswerRequest(request, ctx, route, previousResponseId, state);
        EnglishAssistantAnswerResult answer = answerService.answer(answerRequest);
        persistConversationState(request, route, draftHash, state, answerRequest, answer);
        return success(request, route, answer, shouldUseDraftContext(route, request));
    }

    public EnglishAssistantChatResponse stream(EnglishAssistantChatRequest request,
                                               RequestContext ctx,
                                               Consumer<EnglishAssistantRouterResult> onMeta,
                                               EnglishAssistantStreamListener listener) {
        EnglishAssistantConversationState state = conversationStore.getState(request.getConversationId());
        String routerPreviousResponseId = resolveRouterPreviousResponseId(request, state);
        EnglishAssistantRouterResult route = applyRouteOverrides(
                scopeRouter.route(request, ctx, routerPreviousResponseId, hasReusableArtifact(state)),
                request,
                state
        );
        if (isSensitiveRefuse(route)) {
            return refused(request.getConversationId(), route, SENSITIVE_REFUSE_MESSAGE);
        }
        if (isOffTopic(route)) {
            return refused(request.getConversationId(), route, OFF_TOPIC_MESSAGE);
        }
        if (needsDraftButDisabled(route, request)) {
            return refused(request.getConversationId(), route, DRAFT_CONTEXT_REQUIRED_MESSAGE);
        }

        String draftHash = shouldUseDraftContext(route, request) ? hashDraft(request.getDraftText()) : null;
        if (isDraftScope(route) && state != null && state.lastDraftHash() != null && draftHash != null
                && !state.lastDraftHash().equals(draftHash)) {
            conversationStore.clearDraftState(request.getConversationId());
            state = state.withoutDraftChain();
        }
        String previousResponseId = resolvePreviousResponseId(route, state, draftHash);
        EnglishAssistantAnswerRequest answerRequest = buildAnswerRequest(request, ctx, route, previousResponseId, state);

        if (onMeta != null) {
            onMeta.accept(route);
        }
        EnglishAssistantAnswerResult answer = answerService.streamAnswer(answerRequest, listener);
        persistConversationState(request, route, draftHash, state, answerRequest, answer);
        return success(request, route, answer, shouldUseDraftContext(route, request));
    }

    private EnglishAssistantAnswerRequest buildAnswerRequest(EnglishAssistantChatRequest request,
                                                             RequestContext ctx,
                                                             EnglishAssistantRouterResult route,
                                                             String previousResponseId,
                                                             EnglishAssistantConversationState state) {
        EnglishAssistantAnswerRequest answerRequest = new EnglishAssistantAnswerRequest();
        answerRequest.setConversationId(request.getConversationId());
        answerRequest.setScope(route.scope());
        answerRequest.setTaskType(route.taskType());
        answerRequest.setUseDraftContext(shouldUseDraftContext(route, request));
        answerRequest.setMessage(request.getMessage());
        answerRequest.setAssignmentText(answerRequest.getUseDraftContext() ? request.getAssignmentText() : null);
        answerRequest.setSelectedText(answerRequest.getUseDraftContext() ? request.getSelectedText() : null);
        answerRequest.setDraftText(answerRequest.getUseDraftContext() ? request.getDraftText() : null);
        answerRequest.setAssistantOutputText(resolveAssistantOutputText(route, state));
        answerRequest.setArtifactChain(resolveArtifactChain(route, state));
        applyRubricContext(answerRequest, route, request, ctx);
        answerRequest.setPreviousResponseId(previousResponseId);
        answerRequest.setPromptCacheKey(resolvePromptCacheKey(route));
        answerRequest.setUserId(ctx == null ? null : ctx.getUserId());
        answerRequest.setTraceId(ctx == null ? null : ctx.getRequestId());

        String chain = resolveChain(route, state);
        if (supportsSummary(route, chain)) {
            answerRequest.setSummaryText(resolveCurrentSummary(chain, state));
        }
        EnglishAssistantContextBundle bundle = contextAssembler.assemble(answerRequest, state);
        if (supportsSummary(route, chain)
                && summaryService.shouldGenerate(chain, safeState(state), request, route, bundle.softLimitExceeded())) {
            String generatedSummary = summaryService.buildSummary(
                    chain,
                    resolveCurrentSummary(chain, state),
                    resolveChainTurns(chain, state),
                    request,
                    route,
                    answerRequest
            );
            answerRequest.setSummaryText(generatedSummary);
            bundle = contextAssembler.assemble(answerRequest, state);
        }
        applyContextBundle(answerRequest, bundle);
        return answerRequest;
    }

    private void applyRubricContext(EnglishAssistantAnswerRequest answerRequest,
                                    EnglishAssistantRouterResult route,
                                    EnglishAssistantChatRequest request,
                                    RequestContext ctx) {
        if (!isDraftScope(route)) {
            return;
        }
        if (!answerRequest.getUseDraftContext()) {
            return;
        }
        EnglishAssistantRubricContext rubricContext =
                rubricContextService.resolve(request.getStudyStage(), request.getWritingMode());
        if (rubricContext == null) {
            log.info("english assistant rubric skipped traceId={} conversationId={} stage={} mode={}",
                    ctx == null ? null : ctx.getRequestId(),
                    request.getConversationId(),
                    request.getStudyStage(),
                    request.getWritingMode());
            return;
        }
        answerRequest.setRubricKey(rubricContext.rubricKey());
        answerRequest.setRubricSummary(rubricContext.summary());
    }

    private EnglishAssistantChatResponse success(EnglishAssistantChatRequest request,
                                                 EnglishAssistantRouterResult route,
                                                 EnglishAssistantAnswerResult answer,
                                                 boolean usedDraftContext) {
        EnglishAssistantChatResponse response = new EnglishAssistantChatResponse();
        response.setConversationId(request.getConversationId());
        response.setResponseId(answer.responseId());
        response.setScope(route.scope());
        response.setTaskType(route.taskType());
        response.setRefused(false);
        response.setRefusalReason(null);
        response.setUsedDraftContext(usedDraftContext);
        response.setMessage(answer.message());
        response.setActions(resolveActions(route.taskType(), answer.message()));
        return response;
    }

    private void persistConversationState(EnglishAssistantChatRequest request,
                                          EnglishAssistantRouterResult route,
                                          String draftHash,
                                          EnglishAssistantConversationState state,
                                          EnglishAssistantAnswerRequest answerRequest,
                                          EnglishAssistantAnswerResult answer) {
        EnglishAssistantConversationState current = safeState(state);
        String chain = resolveChain(route, current);
        EnglishAssistantTurn turn = new EnglishAssistantTurn(request.getMessage(), answer.message(), route.scope(), route.taskType());
        if ("draft".equals(chain)) {
            conversationStore.saveDraftState(
                    request.getConversationId(),
                    answer.responseId(),
                    draftHash == null ? current.lastDraftHash() : draftHash,
                    answer.message(),
                    resolveArtifactText(route, answer),
                    resolveArtifactTaskType(route, answer),
                    turn,
                    answerRequest.getSummaryText(),
                    current.draftTurnCount() + 1,
                    answerRequest.getTrimmedContextMode() != null && !"full".equals(answerRequest.getTrimmedContextMode())
                            ? current.draftSoftOverflowCount() + 1
                            : 0
            );
            return;
        }
        conversationStore.saveGeneralState(
                request.getConversationId(),
                answer.responseId(),
                answer.message(),
                resolveArtifactText(route, answer),
                resolveArtifactTaskType(route, answer),
                turn,
                answerRequest.getSummaryText(),
                current.generalTurnCount() + 1,
                answerRequest.getTrimmedContextMode() != null && !"full".equals(answerRequest.getTrimmedContextMode())
                        ? current.generalSoftOverflowCount() + 1
                        : 0
        );
    }

    private EnglishAssistantChatResponse refused(String conversationId,
                                                 EnglishAssistantRouterResult route,
                                                 String message) {
        EnglishAssistantChatResponse response = new EnglishAssistantChatResponse();
        response.setConversationId(conversationId);
        response.setScope(route.scope());
        response.setTaskType(route.taskType());
        response.setRefused(true);
        response.setRefusalReason(route.refusalReason());
        response.setUsedDraftContext(false);
        response.setMessage(message);
        response.setActions(List.of());
        return response;
    }

    private List<EnglishAssistantUiAction> resolveActions(String taskType, String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<EnglishAssistantUiAction> actions = new ArrayList<>();
        switch (normalize(taskType)) {
            case "rewrite" -> actions.add(new EnglishAssistantUiAction("apply_rewrite", "应用改写", message.trim()));
            case "polish" -> actions.add(new EnglishAssistantUiAction("apply_rewrite", "应用润色", message.trim()));
            case "translate" -> actions.add(new EnglishAssistantUiAction("insert_translation", "插入翻译", message.trim()));
            default -> {
            }
        }
        return actions;
    }

    private String resolveRouterPreviousResponseId(EnglishAssistantChatRequest request,
                                                   EnglishAssistantConversationState state) {
        if (state == null) {
            return null;
        }
        if (hasAssistantOutputReference(request.getMessage()) && hasReusableArtifact(state)) {
            return state.lastArtifactResponseId();
        }
        if (Boolean.TRUE.equals(request.getUseDraftContext()) && hasDraftMaterial(request)) {
            String draftHash = hashDraft(request.getDraftText());
            if (state.lastDraftHash() != null && draftHash != null && !state.lastDraftHash().equals(draftHash)) {
                return null;
            }
            if (state.draftLastResponseId() != null && !state.draftLastResponseId().isBlank()) {
                return state.draftLastResponseId();
            }
        }
        return state.generalLastResponseId();
    }

    private String resolvePreviousResponseId(EnglishAssistantRouterResult route,
                                             EnglishAssistantConversationState state,
                                             String draftHash) {
        if (state == null) {
            return null;
        }
        if (isAssistantOutputScope(route)) {
            return resolveAssistantOutputResponseId(state);
        }
        if (isDraftScope(route)) {
            if (state.lastDraftHash() != null && draftHash != null && !state.lastDraftHash().equals(draftHash)) {
                return null;
            }
            return state.draftLastResponseId();
        }
        return state.generalLastResponseId();
    }

    private EnglishAssistantRouterResult applyRouteOverrides(EnglishAssistantRouterResult route,
                                                             EnglishAssistantChatRequest request,
                                                             EnglishAssistantConversationState state) {
        if (route == null) {
            return null;
        }
        if (shouldForceAssistantOutputScope(route, request, state)) {
            return new EnglishAssistantRouterResult(
                    "assistant_output",
                    resolveOverrideTaskType(request, route),
                    false,
                    null
            );
        }
        if (!shouldForceDraftScope(request)) {
            return route;
        }
        if (!isOffTopic(route)) {
            return route;
        }
        return new EnglishAssistantRouterResult(
                "current_draft",
                resolveOverrideTaskType(request, route),
                true,
                null
        );
    }

    private boolean isOffTopic(EnglishAssistantRouterResult route) {
        return route == null || "off_topic".equals(route.scope());
    }

    private boolean isSensitiveRefuse(EnglishAssistantRouterResult route) {
        return route != null && "sensitive_refuse".equals(route.scope());
    }

    private boolean isAssistantOutputScope(EnglishAssistantRouterResult route) {
        return route != null && "assistant_output".equals(route.scope());
    }

    private boolean isDraftScope(EnglishAssistantRouterResult route) {
        return route != null && "current_draft".equals(route.scope());
    }

    private boolean needsDraftButDisabled(EnglishAssistantRouterResult route, EnglishAssistantChatRequest request) {
        return isDraftScope(route) && !Boolean.TRUE.equals(request.getUseDraftContext());
    }

    private boolean shouldUseDraftContext(EnglishAssistantRouterResult route, EnglishAssistantChatRequest request) {
        return isDraftScope(route) && Boolean.TRUE.equals(request.getUseDraftContext());
    }

    private boolean shouldForceDraftScope(EnglishAssistantChatRequest request) {
        if (!Boolean.TRUE.equals(request.getUseDraftContext())) {
            return false;
        }
        if (!hasDraftMaterial(request)) {
            return false;
        }
        String normalizedMessage = normalizeMessage(request.getMessage());
        if (normalizedMessage.isEmpty()) {
            return false;
        }
        return containsAny(normalizedMessage,
                "这篇作文", "这篇文章", "当前作文", "全文",
                "这句", "这一句", "这段", "这一段", "上一段", "上文",
                "字数", "多少字");
    }

    private boolean shouldForceAssistantOutputScope(EnglishAssistantRouterResult route,
                                                    EnglishAssistantChatRequest request,
                                                    EnglishAssistantConversationState state) {
        if (route == null || request == null || state == null) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getUseDraftContext())) {
            return false;
        }
        if (!hasAssistantOutputReference(request.getMessage())) {
            return false;
        }
        if (!hasReusableArtifact(state)) {
            return false;
        }
        return isOffTopic(route) || isDraftScope(route);
    }

    private String resolveAssistantOutputText(EnglishAssistantRouterResult route,
                                              EnglishAssistantConversationState state) {
        if (!isAssistantOutputScope(route) || state == null) {
            return null;
        }
        return state.lastArtifactText();
    }

    private String resolveAssistantOutputResponseId(EnglishAssistantConversationState state) {
        if (state == null) {
            return null;
        }
        return state.lastArtifactResponseId();
    }

    private String resolveArtifactChain(EnglishAssistantRouterResult route,
                                        EnglishAssistantConversationState state) {
        if (!isAssistantOutputScope(route) || state == null) {
            return null;
        }
        return state.lastArtifactChain();
    }

    private boolean hasAssistantOutputReference(String message) {
        String normalizedMessage = normalizeMessage(message);
        if (normalizedMessage.isEmpty()) {
            return false;
        }
        return containsAny(normalizedMessage,
                "最后一段", "最后一部分", "最后一节",
                "上面那篇", "上面这篇", "上面的作文", "上一篇",
                "刚才那篇", "刚才这篇", "刚才写的", "刚才生成的",
                "上面那段", "上一条", "上一段");
    }

    private String resolvePromptCacheKey(EnglishAssistantRouterResult route) {
        if (isDraftScope(route)) {
            return "english-answer-draft-v1";
        }
        if (isAssistantOutputScope(route)) {
            return "english-answer-artifact-v1";
        }
        return "english-answer-general-v1";
    }

    private boolean hasDraftMaterial(EnglishAssistantChatRequest request) {
        return !isBlank(request.getDraftText()) || !isBlank(request.getAssignmentText());
    }

    private String resolveOverrideTaskType(EnglishAssistantChatRequest request, EnglishAssistantRouterResult route) {
        String preferredAction = normalize(request.getPreferredAction());
        if (!preferredAction.isEmpty()) {
            return preferredAction;
        }
        String routeTaskType = normalize(route.taskType());
        return routeTaskType.isEmpty() ? "ask" : routeTaskType;
    }

    private boolean hasReusableArtifact(EnglishAssistantConversationState state) {
        return state != null
                && !isBlank(state.lastArtifactResponseId())
                && !isBlank(state.lastArtifactText())
                && !isBlank(state.lastArtifactTaskType());
    }

    private String resolveArtifactText(EnglishAssistantRouterResult route, EnglishAssistantAnswerResult answer) {
        return isReusableArtifactTask(route, answer) ? answer.message() : null;
    }

    private String resolveArtifactTaskType(EnglishAssistantRouterResult route, EnglishAssistantAnswerResult answer) {
        return isReusableArtifactTask(route, answer) ? route.taskType() : null;
    }

    private boolean isReusableArtifactTask(EnglishAssistantRouterResult route, EnglishAssistantAnswerResult answer) {
        if (route == null || answer == null || isBlank(answer.message())) {
            return false;
        }
        String taskType = normalize(route.taskType());
        if (!containsAny(taskType, "generate", "rewrite", "polish", "translate", "evaluate")) {
            return false;
        }
        return answer.message().trim().length() >= 20;
    }

    private void applyContextBundle(EnglishAssistantAnswerRequest answerRequest, EnglishAssistantContextBundle bundle) {
        answerRequest.setAssignmentText(bundle.assignmentText());
        answerRequest.setSelectedText(bundle.selectedText());
        answerRequest.setDraftText(bundle.draftExcerpt());
        answerRequest.setAssistantOutputText(bundle.assistantOutputExcerpt());
        answerRequest.setRubricSummary(bundle.rubricSummary());
        answerRequest.setRecentTurnsText(bundle.recentTurnsText());
        answerRequest.setSummaryText(bundle.summaryText());
        answerRequest.setTrimmedContextMode(bundle.trimmedContextMode());
    }

    private boolean supportsSummary(EnglishAssistantRouterResult route, String chain) {
        return route != null && !"assistant_output".equals(route.scope());
    }

    private String resolveChain(EnglishAssistantRouterResult route,
                                EnglishAssistantConversationState state) {
        if (isDraftScope(route)) {
            return "draft";
        }
        if (isAssistantOutputScope(route) && state != null) {
            return "draft".equals(state.lastArtifactChain()) ? "draft" : "general";
        }
        return "general";
    }

    private String resolveCurrentSummary(String chain, EnglishAssistantConversationState state) {
        if (state == null) {
            return null;
        }
        return "draft".equals(chain) ? state.draftSummary() : state.generalSummary();
    }

    private List<EnglishAssistantTurn> resolveChainTurns(String chain, EnglishAssistantConversationState state) {
        if (state == null) {
            return List.of();
        }
        return "draft".equals(chain) ? state.draftRecentTurns() : state.generalRecentTurns();
    }

    private EnglishAssistantConversationState safeState(EnglishAssistantConversationState state) {
        return state == null ? new EnglishAssistantConversationState(null, null, null, null, null) : state;
    }

    private String normalizeMessage(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String message, String... tokens) {
        for (String token : tokens) {
            if (message.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String hashDraft(String draftText) {
        if (draftText == null || draftText.trim().isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(draftText.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(draftText.trim().hashCode());
        }
    }

    private String normalize(String taskType) {
        return taskType == null ? "" : taskType.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
