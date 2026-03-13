package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminUserRolesUpdateRequest;
import com.personalenglishai.backend.dto.admin.AdminUserStatusUpdateRequest;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.entity.admin.AdminRoles;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminUserService adminUserService;

    public AdminUserController(AdminAuthorizationService adminAuthorizationService, AdminUserService adminUserService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> list(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String registerSource,
            @RequestParam(required = false) String adminRole,
            @RequestParam(required = false) String studyStage,
            @RequestParam(required = false) String lastActiveFrom,
            @RequestParam(required = false) String lastActiveTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.USERS_READ);
        return ResponseEntity.ok(adminUserService.listUsers(keyword, status, registerSource, adminRole, studyStage, lastActiveFrom, lastActiveTo, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> detail(@RequestAttribute("userId") Long adminUserId,
                                                      @PathVariable Long userId) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.USERS_READ);
        Map<String, Object> detail = adminUserService.getUserDetail(userId);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<Void> updateStatus(@RequestAttribute("userId") Long adminUserId,
                                             @PathVariable Long userId,
                                             @RequestBody AdminUserStatusUpdateRequest request,
                                             HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.USERS_WRITE);
        adminUserService.updateUserStatus(adminUserId, userId, request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<Void> updateRoles(@RequestAttribute("userId") Long adminUserId,
                                            @PathVariable Long userId,
                                            @RequestBody AdminUserRolesUpdateRequest request,
                                            HttpServletRequest httpRequest) {
        adminAuthorizationService.requireRole(adminUserId, AdminRoles.SUPER_ADMIN);
        adminUserService.updateUserRoles(adminUserId, userId, request, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
