package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminPromptActiveRequest;
import com.personalenglishai.backend.dto.admin.AdminPromptUpsertRequest;
import com.personalenglishai.backend.entity.EssayPrompt;
import com.personalenglishai.backend.mapper.admin.AdminPromptMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminPromptService {
    private final AdminPromptMapper adminPromptMapper;
    private final AdminAuditService adminAuditService;

    public AdminPromptService(AdminPromptMapper adminPromptMapper, AdminAuditService adminAuditService) {
        this.adminPromptMapper = adminPromptMapper;
        this.adminAuditService = adminAuditService;
    }

    public AdminPageResponse<Map<String, Object>> list(Integer stageId, String keyword, Integer examYear, String task,
                                                       Boolean active, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Map<String, Object>> items = adminPromptMapper.search(stageId, keyword, examYear, task, active, offset, normalizedSize);
        long total = adminPromptMapper.countSearch(stageId, keyword, examYear, task, active);
        return new AdminPageResponse<>(items, total, normalizedPage, normalizedSize);
    }

    public EssayPrompt getById(Long id) {
        return adminPromptMapper.selectById(id);
    }

    public EssayPrompt create(Long adminUserId, AdminPromptUpsertRequest request, HttpServletRequest httpRequest) {
        EssayPrompt prompt = toEntity(null, request);
        adminPromptMapper.insert(prompt);
        adminAuditService.audit(adminUserId, "CREATE_PROMPT", "prompt", String.valueOf(prompt.getId()), null, null, prompt, httpRequest);
        return prompt;
    }

    public EssayPrompt update(Long adminUserId, Long id, AdminPromptUpsertRequest request, HttpServletRequest httpRequest) {
        EssayPrompt before = adminPromptMapper.selectById(id);
        EssayPrompt prompt = toEntity(id, request);
        adminPromptMapper.update(prompt);
        EssayPrompt after = adminPromptMapper.selectById(id);
        adminAuditService.audit(adminUserId, "UPDATE_PROMPT", "prompt", String.valueOf(id), null, before, after, httpRequest);
        return after;
    }

    public void updateActive(Long adminUserId, Long id, AdminPromptActiveRequest request, HttpServletRequest httpRequest) {
        EssayPrompt before = adminPromptMapper.selectById(id);
        adminPromptMapper.updateActive(id, Boolean.TRUE.equals(request.getIsActive()) ? 1 : 0);
        EssayPrompt after = adminPromptMapper.selectById(id);
        adminAuditService.audit(adminUserId, "UPDATE_PROMPT_ACTIVE", "prompt", String.valueOf(id), null, before, after, httpRequest);
    }

    private EssayPrompt toEntity(Long id, AdminPromptUpsertRequest request) {
        EssayPrompt prompt = new EssayPrompt();
        prompt.setId(id);
        prompt.setStageId(request.getStageId());
        prompt.setPaper(request.getPaper());
        prompt.setTitle(request.getTitle());
        prompt.setPromptText(request.getPromptText());
        prompt.setExamYear(request.getExamYear());
        prompt.setImageUrl(request.getImageUrl());
        prompt.setImageDescription(request.getImageDescription());
        prompt.setMaterialText(request.getMaterialText());
        prompt.setTask(request.getTask());
        prompt.setWordCountMin(request.getWordCountMin());
        prompt.setWordCountMax(request.getWordCountMax());
        prompt.setMaxScore(request.getMaxScore());
        prompt.setSource(request.getSource());
        prompt.setIsActive(request.getIsActive() == null ? 1 : request.getIsActive());
        return prompt;
    }
}
