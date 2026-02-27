package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.ai.dto.AICommandResponse;
import com.personalenglishai.backend.ai.orchestrator.AIOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/ai")
public class AICommandController {

    private final AIOrchestrator orchestrator;
    private final Environment environment;

    public AICommandController(AIOrchestrator orchestrator, Environment environment) {
        this.orchestrator = orchestrator;
        this.environment = environment;
    }

    @PostMapping("/command")
    public ResponseEntity<AICommandResponse> command(
            @Valid @RequestBody AICommandRequest request,
            HttpServletRequest httpRequest) {

        RequestContext ctx = buildRequestContext(httpRequest);
        AICommandResponse response = orchestrator.execute(request, ctx);
        return ResponseEntity.ok(response);
    }

    private RequestContext buildRequestContext(HttpServletRequest httpRequest) {
        RequestContext ctx = new RequestContext();
        Long userId = (Long) httpRequest.getAttribute("userId");
        boolean authPresent = hasBearerToken(httpRequest);
        ctx.setAuthPresent(authPresent);

        if (userId == null) {
            if (isDevOrLocal() && !authPresent) {
                ctx.setUserId(0L);
                ctx.setTenantId("mock-tenant");
                ctx.setWorkspaceId("default");
                return ctx;
            }
            throw new IllegalStateException("JWT required");
        }

        String resolvedTenantId = (String) httpRequest.getAttribute("tenantId");
        String resolvedWorkspaceId = (String) httpRequest.getAttribute("workspaceId");
        ctx.setUserId(userId);
        ctx.setTenantId(resolvedTenantId != null && !resolvedTenantId.isBlank() ? resolvedTenantId : String.valueOf(userId));
        ctx.setWorkspaceId(resolvedWorkspaceId != null && !resolvedWorkspaceId.isBlank() ? resolvedWorkspaceId : "default");
        return ctx;
    }

    private boolean isDevOrLocal() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(p -> p.equals("dev") || p.equals("local"));
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
}
