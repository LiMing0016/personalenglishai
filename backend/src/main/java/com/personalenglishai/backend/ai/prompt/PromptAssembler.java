package com.personalenglishai.backend.ai.prompt;

import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Message;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Options;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Result;
import com.personalenglishai.backend.ai.prompt.ReferenceResolver.ReferenceResolution;
import com.personalenglishai.backend.ai.prompt.ReferenceResolver.ReferenceType;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(PromptAssembler.class);

    private final AbilityPromptBuilder abilityPromptBuilder;
    private final ReferenceResolver referenceResolver;
    private final ConversationContextProcessor conversationContextProcessor;
    private final boolean promptDebugEnabled;
    private final int selectedTextMax;
    private final int recentEachMax;
    private final int recentTurns;
    private final int draftMax;

    public PromptAssembler(AbilityPromptBuilder abilityPromptBuilder,
                           ReferenceResolver referenceResolver,
                           ConversationContextProcessor conversationContextProcessor,
                           @Value("${ai.prompt.debug:false}") boolean promptDebugEnabled,
                           @Value("${ai.prompt.context.selected-text-max:1200}") int selectedTextMax,
                           @Value("${ai.prompt.context.recent-each-max:200}") int recentEachMax,
                           @Value("${ai.prompt.context.recent-turns:8}") int recentTurns,
                           @Value("${ai.prompt.context.draft-max:1600}") int draftMax) {
        this.abilityPromptBuilder = abilityPromptBuilder;
        this.referenceResolver = referenceResolver;
        this.conversationContextProcessor = conversationContextProcessor;
        this.promptDebugEnabled = promptDebugEnabled;
        this.selectedTextMax = selectedTextMax;
        this.recentEachMax = recentEachMax;
        this.recentTurns = recentTurns;
        this.draftMax = draftMax;

        log.info("[AI_TRACE] ai.prompt.debug = {} sourceKey=ai.prompt.debug component=PromptAssembler", promptDebugEnabled);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext) {
        return buildChatPromptInput(req, aiContext, (UserAbilityProfile) null, null, null);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext, UserAbilityProfile profile) {
        return buildChatPromptInput(req, aiContext, profile, null, null);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext, UserAbilityProfile profile, Long userId) {
        return buildChatPromptInput(req, aiContext, profile, userId, null);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext, UserAbilityProfile profile, Long userId, String traceId) {
        String abilityPrompt = profile == null ? "" : abilityPromptBuilder.buildAbilityPrompt(profile, userId);
        return buildChatPromptInput(req, aiContext, abilityPrompt, userId, traceId);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext, String abilityPrompt) {
        return buildChatPromptInput(req, aiContext, abilityPrompt, null, null);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext, String abilityPrompt, Long userId) {
        return buildChatPromptInput(req, aiContext, abilityPrompt, userId, null);
    }

    public ChatPromptInput buildChatPromptInput(AICommandRequest req, AIContext aiContext, String abilityPrompt, Long userId, String traceId) {
        String docId = req != null && req.getContextRefs() != null ? req.getContextRefs().getDocId() : null;
        log.info("[AI_TRACE] PromptAssembler hit traceId={} userId={} intent={} docId={}",
                traceId,
                userId,
                req != null ? req.getIntent() : null,
                docId);

        Map<String, Object> constraints = req != null ? req.getConstraints() : null;
        String contextScope = normalizeContextScope(valueOfAny(constraints, "contextScope", "context_scope"));
        String actionOrigin = normalizeActionOrigin(valueOfAny(constraints, "actionOrigin", "action_origin"));
        String selectedText = valueOfAny(constraints, "selectedText", "selection", "selected_text");
        String draftText = resolveDraftText(aiContext, constraints);
        String conversationId = resolveConversationId(constraints, docId);
        List<Message> recentMessagesRaw = parseRecentMessagesRaw(constraints);
        Result conversationResult = conversationContextProcessor.process(
                recentMessagesRaw,
                new Options(recentTurns, conversationId, traceId)
        );
        List<Message> recentMessages = conversationResult.messages();

        String userMessage = defaultIfBlank(req != null ? req.getInstruction() : null, "");
        String taskIntent = detectTaskIntent(defaultIfBlank(req != null ? req.getIntent() : null, "chat"), userMessage);
        ReferenceResolution reference = referenceResolver.resolve(userMessage);
        boolean hasDocId = !isBlank(docId);
        String target = detectTarget(taskIntent, userMessage, selectedText, draftText, recentMessages, reference, contextScope, actionOrigin, hasDocId);

        ContextDecision contextDecision = decideContext(taskIntent, userMessage, selectedText, recentMessages, reference, draftText, contextScope, actionOrigin, hasDocId);
        String contextBlock = buildContextBlock(contextDecision, target, selectedText, draftText, recentMessages, reference);

        // system prompt: role definition + ability profile + output rules
        StringBuilder sys = new StringBuilder();
        sys.append(PromptTemplates.SYSTEM_PROMPT_V1)
                .append("\n\n").append(PromptTemplates.ROLE_CHAT_V1);
        if (!isBlank(abilityPrompt)) {
            sys.append("\n\n").append(abilityPrompt);
        }
        sys.append("\n\n").append(PromptTemplates.OUTPUT_RULES_V1);
        String systemPrompt = sys.toString();

        // user prompt: context + user message
        StringBuilder user = new StringBuilder();
        if (!isBlank(contextBlock)) {
            user.append(contextBlock).append("\n\n");
        }
        user.append(userMessage);
        String finalPrompt = user.toString();
        String scope = isBlank(selectedText) ? "document" : "selection";

        boolean hasDraftText = !isBlank(draftText);
        int draftLen = hasDraftText ? draftText.length() : 0;
        boolean hasSelectedText = !isBlank(selectedText);
        int selectedLen = hasSelectedText ? selectedText.length() : 0;
        int contextLen = isBlank(contextBlock) ? 0 : contextBlock.length();

        if (promptDebugEnabled) {
            log.info("[PROMPT_BUILD] traceId={} userId={} contextScope={} actionOrigin={} contextInjected={} injectSelectedContext={} injectDraftContext={} injectConversationContext={} target={} contextLen={} hasDocId={} hasDraftText={} draftLen={} hasSelectedText={} selectedLen={} referenceType={} referenceConfidence={} recentInputCount={} recentOutputCount={} conversationProcessor={} conversationFallbackUsed={} taskIntent={} policy={}",
                    traceId,
                    userId,
                    contextScope,
                    actionOrigin,
                    contextDecision.anyInjected(),
                    contextDecision.injectSelectedContext(),
                    contextDecision.injectDraftContext(),
                    contextDecision.injectConversationContext(),
                    target,
                    contextLen,
                    hasDocId,
                    hasDraftText,
                    draftLen,
                    hasSelectedText,
                    selectedLen,
                    reference.type(),
                    String.format(Locale.ROOT, "%.2f", reference.confidence()),
                    recentMessagesRaw.size(),
                    recentMessages.size(),
                    conversationResult.processorName(),
                    conversationResult.fallbackUsed(),
                    taskIntent,
                    PromptTemplates.CHAT_POLICY_TAG);
        } else {
            log.info("[PROMPT_BUILD] skipped (ai.prompt.debug=false) traceId={}", traceId);
        }

        return new ChatPromptInput(scope, selectedText, draftText, systemPrompt, finalPrompt);
    }

    public String buildRepairUserPrompt(String previousOutput) {
        return previousOutput == null ? "" : previousOutput;
    }

    private ContextDecision decideContext(String taskIntent,
                                          String userMessage,
                                          String selectedText,
                                          List<Message> recentMessages,
                                          ReferenceResolution reference,
                                          String draftText,
                                          String contextScope,
                                          String actionOrigin,
                                          boolean hasDocId) {
        String intent = taskIntent == null ? "chat" : taskIntent.toLowerCase(Locale.ROOT);
        boolean hasSelected = !isBlank(selectedText);
        boolean hasRecent = recentMessages != null && !recentMessages.isEmpty();
        boolean hasDraft = !isBlank(draftText);
        boolean hasDraftSignal = hasDraft || hasDocId;
        boolean hasReference = reference != null && reference.hasReference() && reference.confidence() >= 0.6d;
        boolean implicitConversationContinuation = hasRecent
                && !hasSelected
                && "chat_input".equals(actionOrigin)
                && !hasReference
                && isImplicitConversationContinuationIntent(intent, userMessage);

        if ("selection".equals(contextScope)) {
            return new ContextDecision(hasSelected, false, false);
        }
        if ("fulldraft".equals(contextScope)) {
            return new ContextDecision(false, false, hasDraftSignal);
        }
        if ("conversation".equals(contextScope)) {
            return new ContextDecision(false, hasRecent, false);
        }
        if ("none".equals(contextScope)) {
            return new ContextDecision(false, false, false);
        }

        boolean toolbarOrEvaluate = actionOrigin != null
                && (actionOrigin.startsWith("toolbar_") || "evaluate_button".equals(actionOrigin));
        if (toolbarOrEvaluate && hasDraftSignal && !hasSelected) {
            return new ContextDecision(false, false, true);
        }

        boolean injectSelected = hasSelected && ("rewrite".equals(intent) || "explain".equals(intent) || "translate".equals(intent));
        boolean injectConversation = hasRecent && (hasReference || implicitConversationContinuation);
        boolean injectDraft = !hasSelected && hasDraftSignal
                && ("rewrite".equals(intent) || "evaluate".equals(intent) || "chat".equals(intent) || "translate".equals(intent) || "explain".equals(intent));
        if (implicitConversationContinuation) {
            injectDraft = false;
        }

        return new ContextDecision(injectSelected, injectConversation, injectDraft);
    }

    private String buildContextBlock(ContextDecision decision,
                                     String target,
                                     String selectedText,
                                     String draftText,
                                     List<Message> recentMessages,
                                     ReferenceResolution reference) {
        if (decision == null || !decision.anyInjected()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[META]\n");
        sb.append("- Current user input has highest priority.\n");
        sb.append("- selectedText is the editing focus when present.\n");
        sb.append("- draftText is the writing area content when present.\n");
        sb.append("- recentMessages are only for reference resolution.\n");
        sb.append("- Source priority: userInput > selectedText > recentMessages > draftText\n");
        if (reference != null && reference.hasReference()) {
            sb.append("- ReferenceResolution: type=").append(reference.type())
                    .append(", confidence=")
                    .append(String.format(Locale.ROOT, "%.2f", reference.confidence()));
            if (reference.index() != null) {
                sb.append(", index=").append(reference.index());
            }
            sb.append('\n');
        }
        if ("selectedText".equals(target)) {
            sb.append("- Boundary: target=selectedText, only process selected text, do not expand to full draft.\n");
        } else if ("fullDraft".equals(target)) {
            sb.append("- Boundary: target=fullDraft, process the draft text as the main content.\n");
        }
        if (decision.injectSelectedContext() && !isBlank(selectedText)) {
            sb.append("[SELECTED_TEXT] <<<")
                    .append(compactText(selectedText, selectedTextMax))
                    .append(">>> [/SELECTED_TEXT]\n");
        }
        if (decision.injectConversationContext() && recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("[RECENT_MESSAGES] count=").append(recentMessages.size()).append('\n');
            Message lastAssistant = findLastAssistant(recentMessages);
            if (lastAssistant != null) {
                sb.append("  - LastAssistant(priority): ")
                        .append(compactText(lastAssistant.content(), recentEachMax))
                        .append('\n');
            }
            for (int i = 0; i < recentMessages.size(); i++) {
                Message m = recentMessages.get(i);
                sb.append("  - #").append(i + 1).append(' ').append(m.role()).append(": ")
                        .append(compactText(m.content(), recentEachMax)).append('\n');
            }
            sb.append("[/RECENT_MESSAGES]\n");
        }
        if (decision.injectDraftContext() && !isBlank(draftText)) {
            sb.append("[DRAFT_TEXT] <<<")
                    .append(compactText(draftText, draftMax))
                    .append(">>> [/DRAFT_TEXT]\n");
        }
        return sb.toString().trim();
    }

    private String detectTaskIntent(String rawIntent, String userMessage) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (containsAny(msg, "translate", "translation") || containsAny(userMessage, "\u7ffb\u8bd1")) return "translate";
        if (containsAny(msg, "rewrite", "revise", "polish") || containsAny(userMessage, "\u6539\u5199", "\u6da6\u8272", "\u91cd\u5199")) return "rewrite";
        if (containsAny(msg, "summarize", "summary") || containsAny(userMessage, "\u603b\u7ed3", "\u6982\u62ec", "\u6458\u8981")) return "summarize";
        if (containsAny(msg, "explain", "grammar", "why") || containsAny(userMessage, "\u89e3\u91ca", "\u8bed\u6cd5", "\u4e3a\u4ec0\u4e48")) return "explain";
        if (containsAny(msg, "generate", "write") || containsAny(userMessage, "\u751f\u6210", "\u5199\u4e00\u6bb5", "\u5199\u4f5c")) return "generate";
        if (containsAny(msg, "evaluate", "score", "grade") || containsAny(userMessage, "\u8bc4\u4ef7", "\u6253\u5206", "\u8bc4\u5206")) return "evaluate";
        if (!isBlank(rawIntent) && !"chat".equalsIgnoreCase(rawIntent)) return rawIntent.trim().toLowerCase(Locale.ROOT);
        return "chat";
    }

    private String detectTarget(String taskIntent,
                                String userMessage,
                                String selectedText,
                                String draftText,
                                List<Message> recentMessages,
                                ReferenceResolution reference,
                                String contextScope,
                                String actionOrigin,
                                boolean hasDocId) {
        boolean hasDraftSignal = !isBlank(draftText) || hasDocId;
        if ("selection".equals(contextScope)) {
            return !isBlank(selectedText) ? "selectedText" : "none";
        }
        if ("fulldraft".equals(contextScope)) {
            return hasDraftSignal ? "fullDraft" : "none";
        }
        if ("conversation".equals(contextScope)) {
            return "conversationOnly";
        }
        if ("none".equals(contextScope)) {
            return "none";
        }
        if (actionOrigin != null
                && (actionOrigin.startsWith("toolbar_") || "evaluate_button".equals(actionOrigin))
                && hasDraftSignal
                && isBlank(selectedText)) {
            return "fullDraft";
        }
        if (!isBlank(selectedText)) return "selectedText";
        boolean implicitConversationContinuation = "chat_input".equals(actionOrigin)
                && recentMessages != null
                && !recentMessages.isEmpty()
                && isBlank(selectedText)
                && (reference == null || !reference.hasReference())
                && isImplicitConversationContinuationIntent(
                        taskIntent == null ? "chat" : taskIntent.toLowerCase(Locale.ROOT),
                        userMessage
                );
        if (implicitConversationContinuation) {
            return findLastAssistant(recentMessages) != null ? "lastAssistantAnswer" : "conversationOnly";
        }
        if (reference != null && reference.hasReference()) {
            if (reference.type() == ReferenceType.LAST_ASSISTANT) {
                return findLastAssistant(recentMessages) != null ? "lastAssistantAnswer" : (!isBlank(draftText) ? "fullDraft" : "none");
            }
            if (!isBlank(draftText)) return "fullDraft";
        }
        if (!isBlank(draftText)) return "fullDraft";
        return "none";
    }

    private boolean isImplicitConversationContinuationIntent(String intent, String userMessage) {
        if (intent == null) {
            return false;
        }
        if (!"chat".equals(intent) && !"translate".equals(intent) && !"rewrite".equals(intent) && !"explain".equals(intent)) {
            return false;
        }
        String raw = userMessage == null ? "" : userMessage.trim();
        if (raw.isEmpty()) {
            return false;
        }
        int maxLen = "chat".equals(intent) ? 32 : 24;
        return raw.length() <= maxLen;
    }

    private String resolveDraftText(AIContext aiContext, Map<String, Object> constraints) {
        if (aiContext != null && !isBlank(aiContext.getDraftContent())) {
            return aiContext.getDraftContent();
        }
        return valueOfAny(constraints, "draftText", "docText", "fullText", "writingContext", "context");
    }

    private String resolveConversationId(Map<String, Object> constraints, String docId) {
        String fromConstraints = valueOfAny(constraints, "conversationId", "conversation_id");
        if (!isBlank(fromConstraints)) {
            return fromConstraints;
        }
        return isBlank(docId) ? null : "doc:" + docId;
    }

    private List<Message> parseRecentMessagesRaw(Map<String, Object> constraints) {
        List<Message> out = new ArrayList<>();
        if (constraints == null) return out;
        Object raw = constraints.get("recentMessages");
        if (!(raw instanceof List<?> list)) return out;
        for (Object item : list) {
            Message msg = toRecentMessage(item);
            if (msg != null) {
                out.add(msg);
            }
        }
        return out;
    }

    private Message toRecentMessage(Object item) {
        if (item instanceof Map<?, ?> m) {
            String role = normalizeRole(stringValue(m.get("role")));
            String content = stringValue(m.get("content"));
            if (!isBlank(content)) {
                return new Message(role, content.trim());
            }
            return null;
        }
        if (item != null) {
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                return new Message("User", text);
            }
        }
        return null;
    }

    private Message findLastAssistant(List<Message> recentMessages) {
        if (recentMessages == null) return null;
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            Message m = recentMessages.get(i);
            if ("Assistant".equals(m.role())) return m;
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) return false;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) return true;
        }
        return false;
    }

    private String compactText(String text, int maxLen) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ').trim();
        if (maxLen <= 0) return "";
        if (normalized.length() <= maxLen) return normalized;
        return normalized.substring(0, maxLen) + "...";
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s == null ? null : s.trim();
    }

    private String normalizeRole(String role) {
        if (isBlank(role)) return "User";
        String r = role.trim().toLowerCase(Locale.ROOT);
        if (r.contains("assistant") || r.contains("ai")) return "Assistant";
        return "User";
    }

    private String normalizeContextScope(String raw) {
        if (isBlank(raw)) return "auto";
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if ("selection".equals(v) || "conversation".equals(v) || "none".equals(v) || "auto".equals(v)) {
            return v;
        }
        if ("fulldraft".equals(v) || "full_draft".equals(v) || "full-draft".equals(v)) {
            return "fulldraft";
        }
        return "auto";
    }

    private String normalizeActionOrigin(String raw) {
        if (isBlank(raw)) return "unknown";
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private String valueOfAny(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) return null;
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                String value = String.valueOf(map.get(key)).trim();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record ChatPromptInput(String scope, String selectedText, String fullText,
                                    String systemPrompt, String userPrompt) {
        public boolean hasSelectedText() {
            return selectedText != null && !selectedText.isBlank();
        }
    }

}
