package com.personalenglishai.backend.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @Test
    void shouldAllowUploadedStaticAssetsWithoutJwt() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, new ObjectMapper(), environment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/uploads/prompt-sheets/charts/example.png");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        FilterChain chain = (ServletRequest req, ServletResponse res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
