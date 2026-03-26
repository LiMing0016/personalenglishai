package com.personalenglishai.backend.ai.englishassistant;

public interface EnglishAssistantConversationStore {

    EnglishAssistantConversationState getState(String conversationId);

    void saveGeneralState(String conversationId,
                          String responseId,
                          String assistantOutput,
                          String artifactText,
                          String artifactTaskType,
                          EnglishAssistantTurn turn,
                          String summary,
                          int turnCount,
                          int softOverflowCount);

    void saveDraftState(String conversationId,
                        String responseId,
                        String draftHash,
                        String assistantOutput,
                        String artifactText,
                        String artifactTaskType,
                        EnglishAssistantTurn turn,
                        String summary,
                        int turnCount,
                        int softOverflowCount);

    void clearDraftState(String conversationId);
}
