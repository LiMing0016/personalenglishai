package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminEssayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/essays")
public class AdminEssayController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminEssayService adminEssayService;

    public AdminEssayController(AdminAuthorizationService adminAuthorizationService, AdminEssayService adminEssayService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.adminEssayService = adminEssayService;
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> list(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String studyStage,
            @RequestParam(required = false) Integer scoreMin,
            @RequestParam(required = false) Integer scoreMax,
            @RequestParam(required = false) Boolean favorited,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.ESSAYS_READ);
        return ResponseEntity.ok(adminEssayService.list(userId, keyword, mode, studyStage, scoreMin, scoreMax, favorited, archived, createdFrom, createdTo, page, size));
    }

    @GetMapping("/{evaluationId}")
    public ResponseEntity<Map<String, Object>> detail(@RequestAttribute("userId") Long adminUserId,
                                                      @PathVariable Long evaluationId,
                                                      HttpServletRequest request) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.ESSAYS_READ);
        Map<String, Object> data = adminEssayService.detail(adminUserId, evaluationId, request);
        return data == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(data);
    }

    @GetMapping("/{evaluationId}/task")
    public ResponseEntity<Map<String, Object>> task(@RequestAttribute("userId") Long adminUserId,
                                                    @PathVariable Long evaluationId,
                                                    HttpServletRequest request) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.ESSAYS_READ);
        Map<String, Object> data = adminEssayService.task(adminUserId, evaluationId, request);
        return data == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(data);
    }
}
