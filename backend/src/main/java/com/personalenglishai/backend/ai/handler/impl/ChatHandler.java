package com.personalenglishai.backend.ai.handler.impl;

import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.dto.AiResult;
import com.personalenglishai.backend.ai.dto.FinalResult;
import com.personalenglishai.backend.ai.handler.IntentHandler;
import com.personalenglishai.backend.ai.prompt.PromptAssembler;
import com.personalenglishai.backend.ai.prompt.PromptTemplates;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Message;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.service.UserAbilityProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ChatHandler implements IntentHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);

    private final OpenAiClient openAiClient;
    private final PromptAssembler promptAssembler;
    private final ConversationContextProcessor conversationContextProcessor;
    private final UserAbilityProfileService userAbilityProfileService;
    private final boolean promptDebugEnabled;

    public ChatHandler(OpenAiClient openAiClient,
                       PromptAssembler promptAssembler,
                       ConversationContextProcessor conversationContextProcessor,
                       UserAbilityProfileService userAbilityProfileService,
                       @Value("${ai.prompt.debug:false}") boolean promptDebugEnabled) {
        this.openAiClient = openAiClient;
        this.promptAssembler = promptAssembler;
        this.conversationContextProcessor = conversationContextProcessor;
        this.userAbilityProfileService = userAbilityProfileService;
        this.promptDebugEnabled = promptDebugEnabled;

        log.info("[AI_TRACE] ai.prompt.debug = {} sourceKey=ai.prompt.debug component=ChatHandler", promptDebugEnabled);
    }

    @Override
    public AICommandResponse handle(AICommandRequest req, RequestContext ctx, AIContext aiContext) {
        String traceId = ctx.getRequestId();
        long start = System.currentTimeMillis();
        String docId = req != null && req.getContextRefs() != null ? req.getContextRefs().getDocId() : null;
        String conversationId = resolveConversationId(req, docId);

        log.info("[AI_TRACE] AICommand hit traceId={} intent={} docId={}", traceId, req != null ? req.getIntent() : null, docId);

        UserAbilityProfile profile = null;
        try {
            profile = userAbilityProfileService.getByUserId(ctx.getUserId());
        } catch (Exception e) {
            log.debug("load ability profile failed userId={} error={}", ctx.getUserId(), safeError(e));
        }

        if (promptDebugEnabled) {
            log.info("[ABILITY_PROFILE] userId={} stage={} sampleCount={} loaded={}",
                    ctx.getUserId(),
                    profile == null ? null : profile.getStage(),
                    profile == null ? null : profile.getSampleCount(),
                    profile != null);
        }

        PromptAssembler.ChatPromptInput promptInput = promptAssembler.buildChatPromptInput(req, aiContext, profile, ctx.getUserId(), traceId);
        logBeforeOpenAi(traceId, req.getIntent(), promptInput, "main");

        try {
            String output = openAiClient.callWithTraceId(
                    PromptTemplates.CHAT_SYSTEM_PROMPT,
                    promptInput.userPrompt(),
                    traceId,
                    ctx.getXDebugFail()
            );
            log.info("ChatPrompt result traceId={} success=true latencyMs={}",
                    traceId, System.currentTimeMillis() - start);
            appendConversationMemory(conversationId, traceId, req, output);
            return success(output);
        } catch (Exception e) {
            log.error("ChatPrompt result traceId={} success=false latencyMs={} error={}",
                    traceId, System.currentTimeMillis() - start, safeError(e));
            String fallback = "Chat is temporarily unavailable. Please try again.";
            appendConversationMemory(conversationId, traceId, req, fallback);
            return success(fallback);
        }
    }

    private void appendConversationMemory(String conversationId, String traceId, AICommandRequest req, String assistantText) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        List<Message> messages = new ArrayList<>();
        String instruction = req == null ? null : req.getInstruction();
        if (instruction != null && !instruction.isBlank()) {
            messages.add(new Message("User", instruction.trim()));
        }
        if (assistantText != null && !assistantText.isBlank()) {
            messages.add(new Message("Assistant", assistantText.trim()));
        }
        if (messages.isEmpty()) {
            return;
        }
        try {
            conversationContextProcessor.appendMessages(conversationId, messages, traceId);
        } catch (Exception e) {
            log.info("[CTX_PROCESS] traceId={} processor={} action=append ok=false error={}",
                    traceId, "routing", safeError(e));
        }
    }

    private String resolveConversationId(AICommandRequest req, String docId) {
        Map<String, Object> constraints = req == null ? null : req.getConstraints();
        if (constraints != null) {
            Object convId = constraints.get("conversationId");
            if (convId != null) {
                String value = String.valueOf(convId).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return (docId == null || docId.isBlank()) ? null : "doc:" + docId;
    }

    private void logBeforeOpenAi(String traceId,
                                 String intent,
                                 PromptAssembler.ChatPromptInput promptInput,
                                 String stage) {
        int selectedLength = promptInput.selectedText() == null ? 0 : promptInput.selectedText().length();
        log.info("ChatPrompt call stage={} request_id={} intent={} handler_name={} policy_tag={} has_selected_text={} selected_text_length={}",
                stage,
                traceId,
                intent,
                ChatHandler.class.getSimpleName(),
                PromptTemplates.CHAT_POLICY_TAG,
                promptInput.hasSelectedText(),
                selectedLength);
    }

    private String safeError(Throwable t) {
        if (t == null || t.getMessage() == null) {
            return "";
        }
        return t.getMessage().replace("\n", " ").replace("\r", " ");
    }

    private AICommandResponse success(String chatJson) {
        AICommandResponse response = new AICommandResponse();
        response.setStatus("success");

        AiResult result = new AiResult();
        result.setFormat("json");
        result.setApply(chatJson);
        response.setResult(result);

        FinalResult legacy = new FinalResult();
        legacy.setContent(chatJson);
        response.setFinalResult(legacy);
        return response;
    }
}
