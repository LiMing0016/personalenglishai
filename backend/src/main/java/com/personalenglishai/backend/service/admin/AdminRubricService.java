package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.admin.AdminRubricActivateRequest;
import com.personalenglishai.backend.dto.admin.AdminRubricCloneRequest;
import com.personalenglishai.backend.dto.admin.AdminRubricUpdateRequest;
import com.personalenglishai.backend.entity.RubricDimension;
import com.personalenglishai.backend.entity.RubricLevel;
import com.personalenglishai.backend.entity.RubricVersion;
import com.personalenglishai.backend.mapper.admin.AdminRubricMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminRubricService {
    private final AdminRubricMapper adminRubricMapper;
    private final AdminAuditService adminAuditService;

    public AdminRubricService(AdminRubricMapper adminRubricMapper, AdminAuditService adminAuditService) {
        this.adminRubricMapper = adminRubricMapper;
        this.adminAuditService = adminAuditService;
    }

    public AdminPageResponse<Map<String, Object>> list(String stage, String mode, Boolean active, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<Map<String, Object>> items = adminRubricMapper.searchVersions(stage, mode, active, offset, normalizedSize);
        for (Map<String, Object> item : items) {
            Object csv = item.remove("modesCsv");
            item.put("modes", csv == null || String.valueOf(csv).isBlank() ? List.of() : List.of(String.valueOf(csv).split(",")));
        }
        long total = adminRubricMapper.countVersions(stage, mode, active);
        return new AdminPageResponse<>(items, total, normalizedPage, normalizedSize);
    }

    public Map<String, Object> detail(Long id) {
        RubricVersion version = adminRubricMapper.selectVersionById(id);
        if (version == null) return null;
        List<RubricDimension> dimensions = adminRubricMapper.selectDimensionsByVersionId(id);
        List<RubricLevel> levels = adminRubricMapper.selectLevelsByVersionId(id);
        Map<String, List<Map<String, Object>>> levelMap = new LinkedHashMap<>();
        for (RubricLevel level : levels) {
            String key = level.getMode() + "::" + level.getDimensionKey();
            levelMap.computeIfAbsent(key, k -> new ArrayList<>()).add(Map.of(
                    "level", level.getLevel(),
                    "score", level.getLevelScore(),
                    "criteria", level.getCriteria()
            ));
        }

        List<Map<String, Object>> dimensionItems = new ArrayList<>();
        for (RubricDimension dimension : dimensions) {
            String key = dimension.getMode() + "::" + dimension.getDimensionKey();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mode", dimension.getMode());
            item.put("dimensionKey", dimension.getDimensionKey());
            item.put("displayName", dimension.getDisplayName());
            item.put("sortOrder", dimension.getSortOrder());
            item.put("levels", levelMap.getOrDefault(key, List.of()));
            dimensionItems.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", version.getId());
        data.put("rubricKey", version.getRubricKey());
        data.put("stage", version.getStage());
        data.put("isActive", version.getIsActive());
        data.put("dimensions", dimensionItems);
        return data;
    }

    public Map<String, Object> cloneVersion(Long adminUserId, Long id, AdminRubricCloneRequest request, HttpServletRequest httpRequest) {
        RubricVersion source = adminRubricMapper.selectVersionById(id);
        if (source == null) return null;
        RubricVersion target = new RubricVersion();
        target.setRubricKey(request.getRubricKey() == null || request.getRubricKey().isBlank()
                ? source.getRubricKey() + "-copy" : request.getRubricKey().trim());
        target.setStage(source.getStage());
        target.setIsActive(0);
        adminRubricMapper.insertVersion(target);

        List<RubricDimension> dimensions = adminRubricMapper.selectDimensionsByVersionId(id);
        List<RubricLevel> levels = adminRubricMapper.selectLevelsByVersionId(id);
        for (RubricDimension dimension : dimensions) {
            RubricDimension copy = new RubricDimension();
            copy.setRubricVersionId(target.getId());
            copy.setMode(dimension.getMode());
            copy.setDimensionKey(dimension.getDimensionKey());
            copy.setDisplayName(dimension.getDisplayName());
            copy.setSortOrder(dimension.getSortOrder());
            adminRubricMapper.insertDimension(copy);
        }
        for (RubricLevel level : levels) {
            RubricLevel copy = new RubricLevel();
            copy.setRubricVersionId(target.getId());
            copy.setMode(level.getMode());
            copy.setDimensionKey(level.getDimensionKey());
            copy.setLevel(level.getLevel());
            copy.setLevelScore(level.getLevelScore());
            copy.setCriteria(level.getCriteria());
            adminRubricMapper.insertLevel(copy);
        }
        adminAuditService.audit(adminUserId, "CLONE_RUBRIC", "rubric", String.valueOf(target.getId()), null, source,
                Map.of("newId", target.getId(), "rubricKey", target.getRubricKey()), httpRequest);
        return detail(target.getId());
    }

    public Map<String, Object> update(Long adminUserId, Long id, AdminRubricUpdateRequest request, HttpServletRequest httpRequest) {
        RubricVersion before = adminRubricMapper.selectVersionById(id);
        if (before == null) return null;
        if (before.getIsActive() != null && before.getIsActive() == 1) {
            throw new BizException(ErrorCode.ADMIN_ACTIVE_RUBRIC_EDIT_FORBIDDEN);
        }
        RubricVersion updated = new RubricVersion();
        updated.setId(id);
        updated.setRubricKey(request.getRubricKey());
        updated.setStage(request.getStage());
        adminRubricMapper.updateVersionMeta(updated);
        adminRubricMapper.deleteLevelsByVersionId(id);
        adminRubricMapper.deleteDimensionsByVersionId(id);
        if (request.getDimensions() != null) {
            for (AdminRubricUpdateRequest.DimensionInput input : request.getDimensions()) {
                RubricDimension dimension = new RubricDimension();
                dimension.setRubricVersionId(id);
                dimension.setMode(input.getMode());
                dimension.setDimensionKey(input.getDimensionKey());
                dimension.setDisplayName(input.getDisplayName());
                dimension.setSortOrder(input.getSortOrder() == null ? 0 : input.getSortOrder());
                adminRubricMapper.insertDimension(dimension);
                if (input.getLevels() != null) {
                    for (AdminRubricUpdateRequest.LevelInput levelInput : input.getLevels()) {
                        RubricLevel level = new RubricLevel();
                        level.setRubricVersionId(id);
                        level.setMode(input.getMode());
                        level.setDimensionKey(input.getDimensionKey());
                        level.setLevel(levelInput.getLevel());
                        level.setLevelScore(levelInput.getScore());
                        level.setCriteria(levelInput.getCriteria());
                        adminRubricMapper.insertLevel(level);
                    }
                }
            }
        }
        Map<String, Object> after = detail(id);
        adminAuditService.audit(adminUserId, "UPDATE_RUBRIC", "rubric", String.valueOf(id), null, before, after, httpRequest);
        return after;
    }

    public Map<String, Object> activate(Long adminUserId, Long id, AdminRubricActivateRequest request, HttpServletRequest httpRequest) {
        RubricVersion version = adminRubricMapper.selectVersionById(id);
        if (version == null) return null;
        adminRubricMapper.deactivateByStage(version.getStage());
        adminRubricMapper.activateById(id);
        Map<String, Object> after = detail(id);
        adminAuditService.audit(adminUserId, "ACTIVATE_RUBRIC", "rubric", String.valueOf(id), null,
                Map.of("stage", version.getStage(), "modeScope", request.getModeScope()), after, httpRequest);
        return after;
    }
}
