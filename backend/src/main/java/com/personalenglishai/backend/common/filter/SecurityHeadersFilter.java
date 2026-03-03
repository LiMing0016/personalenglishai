package com.personalenglishai.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全响应头 Filter — 所有响应统一添加安全头。
 * <p>
 * 执行顺序在 JWT Filter 之前（Order 0），确保即使鉴权失败也返回安全头。
 */
@Component
@Order(0)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 防止浏览器 MIME 嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 禁止页面被嵌入 iframe（防点击劫持）
        response.setHeader("X-Frame-Options", "DENY");

        // 强制 HTTPS（生产环境由 Nginx 补充更长的 max-age）
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // 禁止浏览器缓存认证相关响应
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) {
            response.setHeader("Cache-Control", "no-store");
        }

        // Referrer 策略：只发送同源 referrer
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // 限制浏览器特性（摄像头、麦克风等）
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        filterChain.doFilter(request, response);
    }
}
