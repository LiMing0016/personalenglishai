package com.personalenglishai.backend.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.parser.AssistantStructuredResponseParser;
import com.personalenglishai.backend.ai.prompt.PromptAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AssistantRuntimeService {

    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final Logger log = LoggerFactory.getLogger(AssistantRuntimeService.class);

    private final AssistantOpenAiClient assistantOpenAiClient;
    private final AssistantConversationStateService conversationStateService;
    private final AssistantToolExecutor toolExecutor;
    private final AssistantStructuredResponseParser responseParser;
    private final String model;

    @Autowired
    public AssistantRuntimeService(AssistantOpenAiClient assistantOpenAiClient,
                                   AssistantConversationStateService conversationStateService,
                                   AssistantToolExecutor toolExecutor,
                                   @Value("${AI_MODEL:" + DEFAULT_MODEL + "}") String model) {
        this(
                assistantOpenAiClient,
                conversationStateService,
                toolExecutor,
                new AssistantStructuredResponseParser(new ObjectMapper()),
                model
        );
    }

    AssistantRuntimeService(AssistantOpenAiClient assistantOpenAiClient,
                            AssistantConversationStateService conversationStateService,
                            AssistantToolExecutor toolExecutor) {
        this(
                assistantOpenAiClient,
                conversationStateService,
                toolExecutor,
                new AssistantStructuredResponseParser(new ObjectMapper()),
                DEFAULT_MODEL
        );
    }

    AssistantRuntimeService(AssistantOpenAiClient assistantOpenAiClient,
                            AssistantConversationStateService conversationStateService,
                            AssistantToolExecutor toolExecutor,
                            AssistantStructuredResponseParser responseParser) {
        this(
                assistantOpenAiClient,
                conversationStateService,
                toolExecutor,
                responseParser,
                DEFAULT_MODEL
        );
    }

    AssistantRuntimeService(AssistantOpenAiClient assistantOpenAiClient,
                            AssistantConversationStateService conversationStateService,
                            AssistantToolExecutor toolExecutor,
                            AssistantStructuredResponseParser responseParser,
                            String model) {
        this.assistantOpenAiClient = assistantOpenAiClient;
        this.conversationStateService = conversationStateService;
        this.toolExecutor = toolExecutor;
        this.responseParser = responseParser;
        this.model = isBlank(model) ? DEFAULT_MODEL : model.trim();
    }

    public AssistantRunResult runChat(PromptAssembler.ChatPromptInput promptInput,
                                      AICommandRequest request,
                                      RequestContext ctx,
                                      AIContext aiContext,
                                      AssistantEventListener listener) {
        String conversationId = resolveConversationId(request, aiContext);
        String previousResponseId = isBlank(conversationId) ? null : conversationStateService.getLastResponseId(conversationId);
        List<AssistantToolRun> toolRuns = new ArrayList<>();
        List<AssistantToolDefinition> tools = AssistantToolCatalog.defaultTools();

        emit(listener, new AssistantEvent("status", "thinking", List.copyOf(toolRuns), null, "thinking"));

        AssistantOpenAiResponse latestResponse = null;
        String latestResponseId = previousResponseId;
        AssistantResponseRequest currentRequest = new AssistantResponseRequest(
                model,
                promptInput.systemPrompt(),
                promptInput.userPrompt(),
                previousResponseId,
                tools,
                List.of(),
                true
        );
        boolean previousResponseRecovered = false;

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            try {
                latestResponse = assistantOpenAiClient.createResponse(currentRequest);
            } catch (RuntimeException e) {
                if (shouldRecoverMissingPreviousResponse(e, conversationId, currentRequest, previousResponseRecovered)) {
                    previousResponseRecovered = true;
                    log.warn("assistant conversation state stale; resetting previous response traceId={} conversationId={} previousResponseId={}",
                            ctx == null ? null : ctx.getRequestId(),
                            conversationId,
                            currentRequest.previousResponseId());
                    conversationStateService.clear(conversationId);
                    currentRequest = withoutPreviousResponse(currentRequest);
                    continue;
                }
                throw e;
            }
            latestResponseId = latestResponse.responseId();

            if (latestResponse.toolCalls() == null || latestResponse.toolCalls().isEmpty()) {
                AssistantStructuredResponse parsed = responseParser.parse(latestResponse.outputText());
                AssistantRunResult result = new AssistantRunResult(
                        latestResponse.responseId(),
                        parsed.message(),
                        parsed.summary(),
                        parsed.actions(),
                        List.copyOf(toolRuns)
                );
                if (!isBlank(conversationId) && !isBlank(latestResponse.responseId())) {
                    conversationStateService.saveLastResponseId(conversationId, latestResponse.responseId());
                }
                emit(listener, new AssistantEvent("final", "completed", List.copyOf(toolRuns), result, parsed.message()));
                return result;
            }

            emit(listener, new AssistantEvent("status", "tool_running", List.copyOf(toolRuns), null, "tool_running"));

            List<AssistantToolOutput> toolOutputs = new ArrayList<>();
            for (AssistantToolCall toolCall : latestResponse.toolCalls()) {
                AssistantToolResult toolResult = toolExecutor.execute(
                        toolCall.name(),
                        toolCall.argumentsJson(),
                        request,
                        ctx,
                        aiContext
                );
                toolRuns.add(new AssistantToolRun(
                        toolCall.name(),
                        "completed",
                        toolResult.summary()
                ));
                toolOutputs.add(new AssistantToolOutput(toolCall.callId(), toolResult.outputJson()));
            }

            emit(listener, new AssistantEvent("tool_runs", "tool_completed", List.copyOf(toolRuns), null, "tool_completed"));

            currentRequest = new AssistantResponseRequest(
                    model,
                    promptInput.systemPrompt(),
                    null,
                    latestResponseId,
                    tools,
                    toolOutputs,
                    true
            );
        }

        AssistantRunResult fallback = new AssistantRunResult(
                latestResponseId,
                "本轮助手处理过长，请重试一次。",
                List.of("工具调用轮次超过上限"),
                List.of(),
                List.copyOf(toolRuns)
        );
        emit(listener, new AssistantEvent("final", "failed", List.copyOf(toolRuns), fallback, fallback.message()));
        return fallback;
    }

    private boolean shouldRecoverMissingPreviousResponse(RuntimeException e,
                                                         String conversationId,
                                                         AssistantResponseRequest request,
                                                         boolean previousResponseRecovered) {
        if (previousResponseRecovered) {
            return false;
        }
        if (isBlank(conversationId) || request == null || isBlank(request.previousResponseId())) {
            return false;
        }
        if (request.toolOutputs() != null && !request.toolOutputs().isEmpty()) {
            return false;
        }
        if (e instanceof AssistantOpenAiException assistantOpenAiException) {
            return "previous_response_not_found".equals(assistantOpenAiException.getErrorCode())
                    || "previous_response_id".equals(assistantOpenAiException.getErrorParam());
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("previous_response_not_found")
                || normalized.contains("param=previous_response_id");
    }

    private AssistantResponseRequest withoutPreviousResponse(AssistantResponseRequest request) {
        return new AssistantResponseRequest(
                request.model(),
                request.instructions(),
                request.inputText(),
                null,
                request.tools(),
                request.toolOutputs(),
                request.store()
        );
    }

    private void emit(AssistantEventListener listener, AssistantEvent event) {
        if (listener != null) {
            listener.onEvent(event);
        }
    }

    private String resolveConversationId(AICommandRequest request, AIContext aiContext) {
        Map<String, Object> constraints = request == null ? null : request.getConstraints();
        if (constraints != null) {
            Object value = constraints.get("conversationId");
            if (value != null) {
                String conversationId = String.valueOf(value).trim();
                if (!conversationId.isEmpty()) {
                    return conversationId;
                }
            }
        }
        String docId = aiContext == null ? null : aiContext.getDocId();
        return isBlank(docId) ? null : "doc:" + docId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
