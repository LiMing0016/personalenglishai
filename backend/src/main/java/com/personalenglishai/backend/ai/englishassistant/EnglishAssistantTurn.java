package com.personalenglishai.backend.ai.englishassistant;

public record EnglishAssistantTurn(
        String userMessage,
        String assistantMessage,
        String scope,
        String taskType
) {

    public EnglishAssistantTurn {
        userMessage = normalize(userMessage);
        assistantMessage = normalize(assistantMessage);
        scope = normalize(scope);
        taskType = normalize(taskType);
    }

    public String toPromptBlock(int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("[turn ").append(index).append("]").append('\n');
        if (!userMessage.isBlank()) {
            sb.append("user: ").append(userMessage).append('\n');
        }
        if (!assistantMessage.isBlank()) {
            sb.append("assistant: ").append(assistantMessage).append('\n');
        }
        return sb.toString().trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
