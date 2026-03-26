package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AIRequestContextResolver {

    private final Environment environment;

    public AIRequestContextResolver(Environment environment) {
        this.environment = environment;
    }

    public RequestContext build(HttpServletRequest httpRequest) {
        RequestContext ctx = new RequestContext();
        Long userId = (Long) httpRequest.getAttribute("userId");
        boolean authPresent = hasBearerToken(httpRequest);
        ctx.setAuthPresent(authPresent);

        if (userId == null) {
            if (allowMockContext(httpRequest)) {
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

    private boolean allowMockContext(HttpServletRequest request) {
        if (!isDevOrLocal()) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/ai/command".equals(path)
                || "/api/ai/command/stream".equals(path)
                || "/api/english-assistant/chat".equals(path)
                || "/api/english-assistant/chat/stream".equals(path);
    }

    private boolean hasBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }
}
