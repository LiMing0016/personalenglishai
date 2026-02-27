package com.personalenglishai.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置
 * JWT 鉴权仅由 JwtAuthenticationFilter 处理，不使用 Interceptor。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
