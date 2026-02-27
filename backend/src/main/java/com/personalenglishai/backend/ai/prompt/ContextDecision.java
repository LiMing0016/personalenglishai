package com.personalenglishai.backend.ai.prompt;

public record ContextDecision(
        boolean injectSelectedContext,
        boolean injectConversationContext,
        boolean injectDraftContext
) {
    public boolean anyInjected() {
        return injectSelectedContext || injectConversationContext || injectDraftContext;
    }
}
