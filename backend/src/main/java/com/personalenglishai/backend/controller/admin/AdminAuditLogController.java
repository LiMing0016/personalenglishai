package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuditLogService;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminAuditLogService adminAuditLogService;

    public AdminAuditLogController(AdminAuthorizationService adminAuthorizationService, AdminAuditLogService adminAuditLogService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.adminAuditLogService = adminAuditLogService;
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> list(@RequestAttribute("userId") Long adminUserId,
                                                                       @RequestParam(required = false) Long targetUserId,
                                                                       @RequestParam(required = false) Long adminUserFilter,
                                                                       @RequestParam(required = false) String action,
                                                                       @RequestParam(required = false) String resourceType,
                                                                       @RequestParam(required = false) String from,
                                                                       @RequestParam(required = false) String to,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.AUDIT_READ);
        return ResponseEntity.ok(adminAuditLogService.list(adminUserFilter, action, resourceType, targetUserId, from, to, page, size));
    }
}
