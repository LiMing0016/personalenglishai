package com.personalenglishai.backend.controller.dto.assistant;

import java.time.LocalDateTime;
import java.util.List;

public class AssistantConversationDetailResponse extends AssistantConversationSummaryResponse {
    private List<AssistantMessageResponse> messages;

    public AssistantConversationDetailResponse(
            String id,
            Long projectId,
            String title,
            String summary,
            boolean pinned,
            boolean archived,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<AssistantMessageResponse> messages) {
        super(id, projectId, title, summary, pinned, archived, createdAt, updatedAt);
        this.messages = messages;
    }

    public List<AssistantMessageResponse> getMessages() {
        return messages;
    }
}
