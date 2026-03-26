package com.personalenglishai.backend.ai.controller;

import com.personalenglishai.backend.ai.context.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AIRequestContextResolverTest {

    @Test
    void buildShouldAllowMockContextForWhitelistedAiEndpointsInDevEvenWhenBearerExists() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        AIRequestContextResolver resolver = new AIRequestContextResolver(environment);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/english-assistant/chat/stream");
        request.addHeader("Authorization", "Bearer expired-token");

        RequestContext context = resolver.build(request);

        assertThat(context.getUserId()).isEqualTo(0L);
        assertThat(context.getTenantId()).isEqualTo("mock-tenant");
        assertThat(context.getWorkspaceId()).isEqualTo("default");
        assertThat(context.isAuthPresent()).isTrue();
    }
}
