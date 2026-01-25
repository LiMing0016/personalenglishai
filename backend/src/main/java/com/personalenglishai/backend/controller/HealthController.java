package com.personalenglishai.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 用于 ECS/SLB 健康检查，不经过 JWT 拦截器
 */
@RestController
public class HealthController {

    /**
     * 健康检查接口
     * GET /health
     * 
     * @return 健康状态信息
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", "backend");
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }
}
