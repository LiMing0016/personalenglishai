package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.orchestrator.AIOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AICommandController {

    private final AIOrchestrator orchestrator;
    private final AIRequestContextResolver requestContextResolver;

    public AICommandController(AIOrchestrator orchestrator, AIRequestContextResolver requestContextResolver) {
        this.orchestrator = orchestrator;
        this.requestContextResolver = requestContextResolver;
    }

    @PostMapping("/command")
    public ResponseEntity<AICommandResponse> command(
            @Valid @RequestBody AICommandRequest request,
            HttpServletRequest httpRequest) {

        RequestContext ctx = requestContextResolver.build(httpRequest);
        AICommandResponse response = orchestrator.execute(request, ctx);
        return ResponseEntity.ok(response);
    }
}
