package com.personalenglishai.backend.ai.assistant;

public interface AssistantOpenAiClient {

    AssistantOpenAiResponse createResponse(AssistantResponseRequest request);
}
