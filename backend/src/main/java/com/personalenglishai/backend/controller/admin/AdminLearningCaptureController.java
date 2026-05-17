package com.personalenglishai.backend.controller.admin;

import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.learning.LearningDeepseekCleaningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/learning-capture")
public class AdminLearningCaptureController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final LearningDeepseekCleaningService cleaningService;

    public AdminLearningCaptureController(
            AdminAuthorizationService adminAuthorizationService,
            LearningDeepseekCleaningService cleaningService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.cleaningService = cleaningService;
    }

    @PostMapping("/deepseek/pending")
    public ResponseEntity<Map<String, Object>> processPending(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(defaultValue = "10") int limit) {
        adminAuthorizationService.requireAdmin(adminUserId);
        int processed = cleaningService.processPendingRuns(limit);
        return ResponseEntity.ok(Map.of("processed", processed));
    }

    @PostMapping("/deepseek/messages/{messageUid}")
    public ResponseEntity<Map<String, Object>> processMessage(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable String messageUid) {
        adminAuthorizationService.requireAdmin(adminUserId);
        boolean processed = cleaningService.processMessage(messageUid);
        return ResponseEntity.ok(Map.of("processed", processed));
    }

    @PostMapping("/deepseek/users/{userId}/days/{date}")
    public ResponseEntity<Map<String, Object>> processUserDay(
            @RequestAttribute("userId") Long adminUserId,
            @PathVariable Long userId,
            @PathVariable LocalDate date,
            @RequestParam(defaultValue = "20") int limit) {
        adminAuthorizationService.requireAdmin(adminUserId);
        int processed = cleaningService.processPendingRunsForUserDay(userId, date, limit);
        return ResponseEntity.ok(Map.of("processed", processed));
    }
}
