package com.personalenglishai.backend.controller.ops;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.service.admin.AdminAuthorizationService;
import com.personalenglishai.backend.service.ops.AgentDebugService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/agent")
public class AgentDebugController {
    private final AdminAuthorizationService adminAuthorizationService;
    private final AgentDebugService agentDebugService;

    public AgentDebugController(AdminAuthorizationService adminAuthorizationService,
                                AgentDebugService agentDebugService) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.agentDebugService = agentDebugService;
    }

    @GetMapping("/runs")
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> listRuns(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String targetAgent,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requireAdmin(adminUserId);
        return ResponseEntity.ok(agentDebugService.listRuns(
                status, intent, targetAgent, model, userId, conversationId, createdFrom, createdTo, page, size));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<Map<String, Object>> runDetail(@RequestAttribute("userId") Long adminUserId,
                                                         @PathVariable String runId) {
        adminAuthorizationService.requireAdmin(adminUserId);
        return ResponseEntity.ok(agentDebugService.getRunDetail(runId));
    }

    @GetMapping("/runs/{runId}/steps")
    public ResponseEntity<List<Map<String, Object>>> runSteps(@RequestAttribute("userId") Long adminUserId,
                                                              @PathVariable String runId) {
        adminAuthorizationService.requireAdmin(adminUserId);
        return ResponseEntity.ok(agentDebugService.listSteps(runId));
    }

    @GetMapping("/runs/{runId}/prompts")
    public ResponseEntity<List<Map<String, Object>>> runPrompts(@RequestAttribute("userId") Long adminUserId,
                                                                @PathVariable String runId) {
        adminAuthorizationService.requireAdmin(adminUserId);
        return ResponseEntity.ok(agentDebugService.listPromptSnapshots(runId));
    }

    @GetMapping("/prompts")
    public ResponseEntity<AdminPageResponse<Map<String, Object>>> listPromptSnapshots(
            @RequestAttribute("userId") Long adminUserId,
            @RequestParam(required = false) String promptKey,
            @RequestParam(required = false) String promptHash,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminAuthorizationService.requireAdmin(adminUserId);
        return ResponseEntity.ok(agentDebugService.listPromptSnapshots(
                promptKey, promptHash, agentName, model, createdFrom, createdTo, page, size));
    }
}
