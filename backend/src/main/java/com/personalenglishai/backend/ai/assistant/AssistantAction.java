package com.personalenglishai.backend.ai.assistant;

public record AssistantAction(
        String type,
        String label,
        String text,
        String panel
) {
}
