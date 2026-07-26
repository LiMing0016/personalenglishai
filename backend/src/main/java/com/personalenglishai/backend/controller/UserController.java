package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.ChangePasswordRequest;
import com.personalenglishai.backend.service.auth.AuthService;
import com.personalenglishai.backend.service.subscription.AiUsageActivityService;
import com.personalenglishai.backend.service.subscription.dto.AiUsageActivityResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 用户账号控制器（修改密码等）
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final AuthService authService;
    private final AiUsageActivityService aiUsageActivityService;

    public UserController(AuthService authService, AiUsageActivityService aiUsageActivityService) {
        this.authService = authService;
        this.aiUsageActivityService = aiUsageActivityService;
    }

    /**
     * 已登录用户修改密码
     * POST /api/users/me/password
     */
    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestAttribute("userId") Long userId) {
        authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/usage")
    public ResponseEntity<ApiResponse<AiUsageActivityResponse>> usage(
            @RequestAttribute("userId") Long userId,
            @RequestParam(defaultValue = "ai_tokens") String metric,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "Asia/Shanghai") String timezone) {
        return ResponseEntity.ok(ApiResponse.success(
                aiUsageActivityService.getActivity(userId, metric, granularity, from, to, timezone)));
    }
}
