package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminMeResponse;
import com.personalenglishai.backend.service.admin.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminUserService adminUserService;

    public AdminAuthController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/me")
    public ResponseEntity<AdminMeResponse> me(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(adminUserService.getMe(userId));
    }
}
