package com.personalenglishai.backend.dto.writing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LinkWritingCoachConversationRequest {
    @NotBlank
    @Size(max = 64)
    private String conversationId;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
