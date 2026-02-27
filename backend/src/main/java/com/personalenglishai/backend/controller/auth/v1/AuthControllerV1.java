package com.personalenglishai.backend.controller.auth.v1;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.auth.dto.LoginRequest;
import com.personalenglishai.backend.controller.auth.dto.LoginResponse;
import com.personalenglishai.backend.controller.auth.dto.RegisterRequest;
import com.personalenglishai.backend.controller.auth.dto.RegisterResponse;
import com.personalenglishai.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器 v1
 * POST /api/v1/auth/register, POST /api/v1/auth/login
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthControllerV1 {

    private final AuthService authService;

    public AuthControllerV1(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );
        RegisterResponse data = new RegisterResponse(userId);
        ApiResponse<RegisterResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * 用户登录
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse data = authService.login(request.getEmail(), request.getPassword());
        ApiResponse<LoginResponse> body = ApiResponse.success(data);
        body.setTraceId(MDC.get("traceId"));
        return ResponseEntity.ok(body);
    }
}
