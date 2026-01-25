package com.personalenglishai.backend.interceptor;

import com.personalenglishai.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 拦截器
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 允许OPTIONS请求通过（CORS预检）
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 获取Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 提取Token
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
}

