package com.personalenglishai.backend.ai.assistant;

@FunctionalInterface
public interface AssistantEventListener {

    void onEvent(AssistantEvent event);
}
