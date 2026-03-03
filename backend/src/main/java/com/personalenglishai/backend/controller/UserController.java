package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.controller.dto.ChangePasswordRequest;
import com.personalenglishai.backend.service.auth.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户账号控制器（修改密码等）
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
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
}
