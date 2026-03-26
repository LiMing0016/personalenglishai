package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantChatRequest;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantChatResponse;
import com.personalenglishai.backend.ai.englishassistant.EnglishAssistantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/english-assistant")
public class EnglishAssistantController {

    private final EnglishAssistantService englishAssistantService;
    private final AIRequestContextResolver requestContextResolver;

    public EnglishAssistantController(EnglishAssistantService englishAssistantService,
                                      AIRequestContextResolver requestContextResolver) {
        this.englishAssistantService = englishAssistantService;
        this.requestContextResolver = requestContextResolver;
    }

    @PostMapping("/chat")
    public ResponseEntity<EnglishAssistantChatResponse> chat(@Valid @RequestBody EnglishAssistantChatRequest request,
                                                             HttpServletRequest httpRequest) {
        RequestContext ctx = requestContextResolver.build(httpRequest);
        return ResponseEntity.ok(englishAssistantService.chat(request, ctx));
    }
}
