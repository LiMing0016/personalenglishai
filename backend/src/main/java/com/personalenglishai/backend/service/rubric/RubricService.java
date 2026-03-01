package com.personalenglishai.backend.service.rubric;

import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.entity.RubricDimension;
import com.personalenglishai.backend.entity.RubricLevel;
import com.personalenglishai.backend.entity.RubricVersion;
import com.personalenglishai.backend.mapper.RubricMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RubricService {

    private final RubricMapper rubricMapper;

    public RubricService(RubricMapper rubricMapper) {
        this.rubricMapper = rubricMapper;
    }

    public RubricActiveResponse getActiveRubric(String stage, String mode) {
        String normalizedStage = normalizeStage(stage);
        String normalizedMode = normalizeMode(mode);

        RubricVersion version = rubricMapper.selectActiveVersionByStage(normalizedStage);
        if (version == null) {
            return null;
        }

        List<RubricDimension> dimensions =
                rubricMapper.selectDimensionsByVersionAndMode(version.getId(), normalizedMode);
        List<RubricLevel> levels =
                rubricMapper.selectLevelsByVersionAndMode(version.getId(), normalizedMode);

        RubricActiveResponse response = new RubricActiveResponse();
        response.setRubricKey(version.getRubricKey());
        response.setMode(normalizedMode);
        response.setDimensions(buildDimensionDtos(dimensions, levels));
        return response;
    }

    private List<RubricActiveResponse.DimensionDto> buildDimensionDtos(
            List<RubricDimension> dimensions,
            List<RubricLevel> levels
    ) {
        Map<String, RubricActiveResponse.DimensionDto> dimensionMap = new LinkedHashMap<>();
        for (RubricDimension d : dimensions) {
            RubricActiveResponse.DimensionDto dto = new RubricActiveResponse.DimensionDto();
            dto.setDimensionKey(d.getDimensionKey());
            dto.setDisplayName(d.getDisplayName());
            dto.setLevels(new ArrayList<>());
            dimensionMap.put(d.getDimensionKey(), dto);
        }

        for (RubricLevel l : levels) {
            RubricActiveResponse.DimensionDto dimensionDto = dimensionMap.get(l.getDimensionKey());
            if (dimensionDto == null) {
                continue;
            }
            RubricActiveResponse.LevelDto levelDto = new RubricActiveResponse.LevelDto();
            levelDto.setLevel(l.getLevel());
            levelDto.setScore(l.getLevelScore());
            levelDto.setCriteria(l.getCriteria());
            dimensionDto.getLevels().add(levelDto);
        }
        return new ArrayList<>(dimensionMap.values());
    }

    public String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if ("free".equals(normalized) || "exam".equals(normalized)) {
            return normalized;
        }
        return "free";
    }

    public boolean isSupportedMode(String mode) {
        if (mode == null) {
            return false;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return "free".equals(normalized) || "exam".equals(normalized);
    }

    public String normalizeStage(String stage) {
        String normalized = stage == null ? "" : stage.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "highschool";
        }
        return normalized;
    }
}
