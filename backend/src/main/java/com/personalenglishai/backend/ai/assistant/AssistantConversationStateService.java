package com.personalenglishai.backend.ai.assistant;

public interface AssistantConversationStateService {

    String getLastResponseId(String conversationId);

    void saveLastResponseId(String conversationId, String responseId);

    void clear(String conversationId);
}
