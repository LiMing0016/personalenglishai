package com.personalenglishai.backend.ai.prompt;

import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Message;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Options;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Result;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Primary
@Component
public class RoutingConversationContextProcessor implements ConversationContextProcessor {

    private final RuleBasedConversationContextProcessor ruleProcessor;
    private final PythonLangChainConversationContextProcessor pythonProcessor;
    private final String mode;
    private final boolean fallbackEnabled;

    public RoutingConversationContextProcessor(
            @Qualifier("ruleConversationContextProcessor") RuleBasedConversationContextProcessor ruleProcessor,
            @Qualifier("pythonLangChainConversationContextProcessor") PythonLangChainConversationContextProcessor pythonProcessor,
            @Value("${ai.context.conversation.processor:rule}") String mode,
            @Value("${ai.context.conversation.python.fallback-enabled:true}") boolean fallbackEnabled
    ) {
        this.ruleProcessor = ruleProcessor;
        this.pythonProcessor = pythonProcessor;
        this.mode = mode == null ? "rule" : mode.trim().toLowerCase(Locale.ROOT);
        this.fallbackEnabled = fallbackEnabled;
    }

    @Override
    public Result process(List<Message> rawMessages, Options options) {
        return switch (mode) {
            case "python" -> processPythonOrFail(rawMessages, options, false);
            case "hybrid" -> processPythonOrFail(rawMessages, options, true);
            default -> ruleProcessor.process(rawMessages, options);
        };
    }

    @Override
    public void appendMessages(String conversationId, List<Message> messages, String traceId) {
        if ("python".equals(mode) || "hybrid".equals(mode)) {
            pythonProcessor.appendMessages(conversationId, messages, traceId);
        }
    }

    private Result processPythonOrFail(List<Message> rawMessages, Options options, boolean fallbackToRule) {
        try {
            Result python = pythonProcessor.process(rawMessages, options);
            return new Result(python.messages(), python.processorName(), false, python.summaryUsed());
        } catch (Exception e) {
            if (!fallbackToRule || !fallbackEnabled) {
                throw e;
            }
            Result fallback = ruleProcessor.process(rawMessages, options);
            return new Result(fallback.messages(), fallback.processorName(), true, fallback.summaryUsed());
        }
    }
}

