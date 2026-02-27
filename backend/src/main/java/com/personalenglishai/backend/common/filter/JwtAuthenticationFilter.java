package com.personalenglishai.backend.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
@Order(1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String CODE_UNAUTHORIZED = "401001";
    private static final String MESSAGE_UNAUTHORIZED = "Unauthorized";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper, Environment environment) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean whitelisted = isWhitelisted(path);
        log.debug("[JWT] method={} path={} whitelisted={}", request.getMethod(), path, whitelisted);

        String authHeader = request.getHeader("Authorization");
        boolean authPresent = authHeader != null && authHeader.startsWith("Bearer ");

        // Whitelisted path: no auth is allowed, but if Bearer is present we still resolve JWT.
        if (whitelisted) {
            if (authPresent) {
                String token = authHeader.substring(7);
                if (!jwtUtil.validateToken(token)) {
                    send401(response);
                    return;
                }
                try {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String nickname = jwtUtil.getNicknameFromToken(token);
                    request.setAttribute("userId", userId);
                    request.setAttribute("nickname", nickname);
                    request.setAttribute("tenantId", String.valueOf(userId));
                    request.setAttribute("workspaceId", "default");
                } catch (Exception e) {
                    send401(response);
                    return;
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (!authPresent) {
            send401(response);
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            send401(response);
            return;
        }

        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String nickname = jwtUtil.getNicknameFromToken(token);
            request.setAttribute("userId", userId);
            request.setAttribute("nickname", nickname);
            request.setAttribute("tenantId", String.valueOf(userId));
            request.setAttribute("workspaceId", "default");
        } catch (Exception e) {
            send401(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        if (path.startsWith("/api/v1/auth/") || path.equals("/health") || path.equals("/api/ping")) {
            return true;
        }

        // dev/local self-test: allow AI command without JWT (mock-tenant)
        if (path.equals("/api/ai/command") && isDevOrLocal()) {
            return true;
        }

        return false;
    }

    private boolean isDevOrLocal() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(p -> p.equals("dev") || p.equals("local"));
    }

    private void send401(HttpServletResponse response) throws IOException {
        ApiResponse<Object> body = ApiResponse.error(CODE_UNAUTHORIZED, MESSAGE_UNAUTHORIZED);
        String tid = MDC.get("traceId");
        if (tid != null) {
            body.setTraceId(tid);
        }
        String json = objectMapper.writeValueAsString(body);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }
}
