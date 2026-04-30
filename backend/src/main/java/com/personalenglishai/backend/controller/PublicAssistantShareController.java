package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.assistant.PublicAssistantShareResponse;
import com.personalenglishai.backend.service.assistant.AssistantConversationService;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/assistant")
public class PublicAssistantShareController {
    private final AssistantConversationService assistantConversationService;

    public PublicAssistantShareController(AssistantConversationService assistantConversationService) {
        this.assistantConversationService = assistantConversationService;
    }

    @GetMapping("/shares/{shareToken}")
    public ResponseEntity<ApiResponse<PublicAssistantShareResponse>> getShare(@PathVariable String shareToken) {
        ApiResponse<PublicAssistantShareResponse> body = ApiResponse.success(
                assistantConversationService.getPublicShare(shareToken));
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }
}
