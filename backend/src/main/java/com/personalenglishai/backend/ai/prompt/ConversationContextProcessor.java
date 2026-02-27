package com.personalenglishai.backend.ai.prompt;

import java.util.List;

public interface ConversationContextProcessor {

    Result process(List<Message> rawMessages, Options options);

    default void appendMessages(String conversationId, List<Message> messages, String traceId) {
        // no-op by default
    }

    record Message(String role, String content) {
    }

    record Options(int recentTurns, String conversationId, String traceId) {
    }

    record Result(List<Message> messages, String processorName, boolean fallbackUsed, boolean summaryUsed) {
    }
}
