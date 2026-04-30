package com.personalenglishai.backend.controller.dto.assistant;

public class SetPinnedAssistantConversationRequest {
    private boolean pinned;

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}
