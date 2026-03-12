package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminRubricActivateRequest;
import com.personalenglishai.backend.dto.admin.AdminRubricCloneRequest;
import com.personalenglishai.backend.dto.admin.AdminRubricUpdateRequest;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminRubricService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/rubrics")
public class AdminRubricController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminRubricService adminRubricService;

    public AdminRubricController(AdminAuthorizationService adminAuthorizationService, AdminRubricService adminRubricService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.adminRubricService = adminRubricService;
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> list(@RequestAttribute("userId") Long adminUserId,
                                                                       @RequestParam(required = false) String stage,
                                                                       @RequestParam(required = false) String mode,
                                                                       @RequestParam(required = false) Boolean active,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.RUBRICS_READ);
        return ResponseEntity.ok(adminRubricService.list(stage, mode, active, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@RequestAttribute("userId") Long adminUserId, @PathVariable Long id) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.RUBRICS_READ);
        Map<String, Object> detail = adminRubricService.detail(id);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<Map<String, Object>> clone(@RequestAttribute("userId") Long adminUserId,
                                                     @PathVariable Long id,
                                                     @RequestBody(required = false) AdminRubricCloneRequest request,
                                                     HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.RUBRICS_WRITE);
        Map<String, Object> data = adminRubricService.cloneVersion(adminUserId, id, request == null ? new AdminRubricCloneRequest() : request, httpRequest);
        return data == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(data);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@RequestAttribute("userId") Long adminUserId,
                                                      @PathVariable Long id,
                                                      @RequestBody AdminRubricUpdateRequest request,
                                                      HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.RUBRICS_WRITE);
        Map<String, Object> data = adminRubricService.update(adminUserId, id, request, httpRequest);
        return data == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(data);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, Object>> activate(@RequestAttribute("userId") Long adminUserId,
                                                        @PathVariable Long id,
                                                        @RequestBody(required = false) AdminRubricActivateRequest request,
                                                        HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.RUBRICS_WRITE);
        Map<String, Object> data = adminRubricService.activate(adminUserId, id, request == null ? new AdminRubricActivateRequest() : request, httpRequest);
        return data == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(data);
    }
}
