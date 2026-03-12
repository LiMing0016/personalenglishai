package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminPromptActiveRequest;
import com.personalenglishai.backend.dto.admin.AdminPromptUpsertRequest;
import com.personalenglishai.backend.entity.EssayPrompt;
import com.personalenglishai.backend.entity.admin.AdminPermissions;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.admin.AdminPromptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/prompts")
public class AdminPromptController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminPromptService adminPromptService;

    public AdminPromptController(AdminAuthorizationService adminAuthorizationService, AdminPromptService adminPromptService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.adminPromptService = adminPromptService;
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> list(@RequestAttribute("userId") Long adminUserId,
                                                                       @RequestParam(required = false) Integer stageId,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Integer examYear,
                                                                       @RequestParam(required = false) String task,
                                                                       @RequestParam(required = false) Boolean active,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.PROMPTS_READ);
        return ResponseEntity.ok(adminPromptService.list(stageId, keyword, examYear, task, active, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EssayPrompt> detail(@RequestAttribute("userId") Long adminUserId, @PathVariable Long id) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.PROMPTS_READ);
        EssayPrompt prompt = adminPromptService.getById(id);
        return prompt == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(prompt);
    }

    @PostMapping
    public ResponseEntity<EssayPrompt> create(@RequestAttribute("userId") Long adminUserId,
                                              @RequestBody AdminPromptUpsertRequest request,
                                              HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.PROMPTS_WRITE);
        return ResponseEntity.ok(adminPromptService.create(adminUserId, request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EssayPrompt> update(@RequestAttribute("userId") Long adminUserId,
                                              @PathVariable Long id,
                                              @RequestBody AdminPromptUpsertRequest request,
                                              HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.PROMPTS_WRITE);
        EssayPrompt prompt = adminPromptService.update(adminUserId, id, request, httpRequest);
        return prompt == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(prompt);
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<Void> updateActive(@RequestAttribute("userId") Long adminUserId,
                                             @PathVariable Long id,
                                             @RequestBody AdminPromptActiveRequest request,
                                             HttpServletRequest httpRequest) {
        adminAuthorizationService.requirePermission(adminUserId, AdminPermissions.PROMPTS_WRITE);
        adminPromptService.updateActive(adminUserId, id, request, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
