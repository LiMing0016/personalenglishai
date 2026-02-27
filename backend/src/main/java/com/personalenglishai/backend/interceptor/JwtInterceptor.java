package com.personalenglishai.backend.interceptor;

import com.personalenglishai.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * JWT 拦截器
 * 仅在需要鉴权的接口上校验 token，公开接口直接放行。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /**
     * 公开接口白名单：仅 /api/v1/auth/**、/health、/api/ping
     */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/health",
            "/api/ping"
    );

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestPath = request.getRequestURI();

        // 允许OPTIONS请求通过（CORS预检）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 检查是否为公开接口（白名单）
        if (isPublicPath(requestPath)) {
            return true; // 公开接口直接放行，不做任何 token 校验
        }

        // 对于需要鉴权的接口，检查是否有 Authorization 头
        String authHeader = request.getHeader("Authorization");
        
        // 如果没有 Authorization 头，直接放行（让后续 Security 或其他逻辑决定是否 401）
        // 这样公开接口即使误入也不会被拦截
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true;
        }

        // 如果有 Authorization 头，则必须验证 token
        String token = authHeader.substring(7);

        // 验证Token
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 将用户信息存储到request中，供后续使用
        Long userId = jwtUtil.getUserIdFromToken(token);
        String nickname = jwtUtil.getNicknameFromToken(token);
        request.setAttribute("userId", userId);
        request.setAttribute("nickname", nickname);

        return true;
    }

    /**
     * 判断是否为公开接口
     */
    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath + "/") || path.equals(publicPath)) {
                return true;
            }
        }
        return false;
    }
}

