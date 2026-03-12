package com.personalenglishai.backend.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.EvaluateTask;
import com.personalenglishai.backend.entity.User;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.EvaluateTaskMapper;
import com.personalenglishai.backend.mapper.UserMapper;
import com.personalenglishai.backend.mapper.admin.AdminEssayMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminEssayService {
    private final AdminEssayMapper adminEssayMapper;
    private final EssayEvaluationMapper essayEvaluationMapper;
    private final EvaluateTaskMapper evaluateTaskMapper;
    private final UserMapper userMapper;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;

    public AdminEssayService(AdminEssayMapper adminEssayMapper,
                             EssayEvaluationMapper essayEvaluationMapper,
                             EvaluateTaskMapper evaluateTaskMapper,
                             UserMapper userMapper,
                             AdminAuditService adminAuditService,
                             ObjectMapper objectMapper) {
        this.adminEssayMapper = adminEssayMapper;
        this.essayEvaluationMapper = essayEvaluationMapper;
        this.evaluateTaskMapper = evaluateTaskMapper;
        this.userMapper = userMapper;
        this.adminAuditService = adminAuditService;
        this.objectMapper = objectMapper;
    }

    public com.personalenglishai.backend.dto.admin.AdminPageResponse<Map<String, Object>> list(Long userId, String keyword,
                                                                                                 String mode, String studyStage,
                                                                                                 Integer scoreMin, Integer scoreMax,
                                                                                                 Boolean favorited, Boolean archived,
                                                                                                 String createdFrom, String createdTo,
                                                                                                 int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Map<String, Object>> items = adminEssayMapper.searchEssays(userId, keyword, mode, studyStage, scoreMin, scoreMax,
                favorited, archived, createdFrom, createdTo, offset, normalizedSize);
        long total = adminEssayMapper.countEssays(userId, keyword, mode, studyStage, scoreMin, scoreMax,
                favorited, archived, createdFrom, createdTo);
        return new com.personalenglishai.backend.dto.admin.AdminPageResponse<>(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> detail(Long adminUserId, Long evaluationId, HttpServletRequest request) {
        EssayEvaluation evaluation = essayEvaluationMapper.selectById(evaluationId);
        if (evaluation == null) return null;
        User user = userMapper.findById(evaluation.getUserId());
        JsonNode result = parseResultJson(evaluation.getResultJson());
        String requestId = result != null && result.hasNonNull("requestId") ? result.get("requestId").asText() : null;
        EvaluateTask task = requestId != null ? evaluateTaskMapper.selectByRequestId(requestId) : null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("evaluationId", evaluation.getId());
        data.put("mode", evaluation.getMode());
        data.put("taskPrompt", evaluation.getTaskPrompt());
        data.put("essayText", evaluation.getEssayText());
        data.put("createdAt", evaluation.getCreatedAt());
        data.put("documentId", evaluation.getDocumentId());
        data.put("requestId", requestId);
        data.put("result", result);
        data.put("user", Map.of(
                "id", evaluation.getUserId(),
                "nickname", user != null ? user.getNickname() : "",
                "email", user != null ? user.getEmail() : ""
        ));
        if (task != null) {
            data.put("taskStatus", task.getStatus());
            data.put("taskError", task.getError());
            data.put("submittedAt", task.getSubmittedAt());
            data.put("completedAt", task.getCompletedAt());
        }
        adminAuditService.audit(adminUserId, "VIEW_ESSAY_DETAIL", "essay", String.valueOf(evaluationId), evaluation.getUserId(), null,
                Map.of("evaluationId", evaluationId), request);
        return data;
    }

    public Map<String, Object> task(Long adminUserId, Long evaluationId, HttpServletRequest request) {
        EssayEvaluation evaluation = essayEvaluationMapper.selectById(evaluationId);
        if (evaluation == null || evaluation.getResultJson() == null) return null;
        JsonNode result = parseResultJson(evaluation.getResultJson());
        String requestId = result != null && result.hasNonNull("requestId") ? result.get("requestId").asText() : null;
        if (requestId == null) return null;
        EvaluateTask task = evaluateTaskMapper.selectByRequestId(requestId);
        if (task == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", task.getRequestId());
        data.put("status", task.getStatus());
        data.put("error", task.getError());
        data.put("submittedAt", task.getSubmittedAt());
        data.put("completedAt", task.getCompletedAt());
        data.put("result", parseResultJson(task.getResultJson()));
        adminAuditService.audit(adminUserId, "VIEW_EVALUATE_TASK", "evaluate_task", task.getRequestId(), evaluation.getUserId(), null,
                Map.of("requestId", task.getRequestId()), request);
        return data;
    }

    private JsonNode parseResultJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
