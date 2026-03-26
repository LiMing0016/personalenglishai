package com.personalenglishai.backend.ai.englishassistant;

import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.service.rubric.RubricService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class EnglishAssistantRubricContextService {

    private final RubricService rubricService;

    public EnglishAssistantRubricContextService(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    public EnglishAssistantRubricContext resolve(String studyStage, String writingMode) {
        String normalizedStage = rubricService.normalizeStage(studyStage);
        String normalizedMode = rubricService.normalizeMode(writingMode);
        RubricActiveResponse rubric = rubricService.getActiveRubric(normalizedStage, normalizedMode);
        if (rubric == null || rubric.getRubricKey() == null || rubric.getRubricKey().isBlank()) {
            return null;
        }
        String summary = buildSummary(normalizedStage, normalizedMode, rubric);
        if (summary.isBlank()) {
            return null;
        }
        return new EnglishAssistantRubricContext(rubric.getRubricKey().trim(), summary);
    }

    private String buildSummary(String stage, String mode, RubricActiveResponse rubric) {
        StringBuilder sb = new StringBuilder();
        sb.append("rubric_key=").append(safe(rubric.getRubricKey())).append('\n');
        sb.append("stage=").append(stage).append('\n');
        sb.append("mode=").append(mode).append('\n');
        sb.append("用法：仅在分析、评价、改写当前作文时参考该 rubric；任务与内容优先于语言亮点。\n");

        if (rubric.getDimensions() != null && !rubric.getDimensions().isEmpty()) {
            sb.append("评分维度：\n");
            for (RubricActiveResponse.DimensionDto dimension : rubric.getDimensions()) {
                if (dimension == null || isBlank(dimension.getDimensionKey())) {
                    continue;
                }
                sb.append("- ")
                        .append(safe(dimension.getDimensionKey()));
                if (!isBlank(dimension.getDisplayName())) {
                    sb.append(" (").append(safe(dimension.getDisplayName())).append(")");
                }
                String anchors = summarizeLevels(dimension.getLevels());
                if (!anchors.isBlank()) {
                    sb.append(": ").append(anchors);
                }
                sb.append('\n');
            }
        }

        appendStageSpecificRules(sb, stage, mode);
        return sb.toString().trim();
    }

    private String summarizeLevels(List<RubricActiveResponse.LevelDto> levels) {
        if (levels == null || levels.isEmpty()) {
            return "";
        }
        return levels.stream()
                .filter(level -> level != null && !isBlank(level.getLevel()) && level.getScore() != null)
                .limit(3)
                .map(level -> {
                    String criteria = clip(level.getCriteria(), 36);
                    return level.getLevel().trim().toUpperCase(Locale.ROOT)
                            + "(" + level.getScore() + "): "
                            + criteria;
                })
                .collect(Collectors.joining(" | "));
    }

    private void appendStageSpecificRules(StringBuilder sb, String stage, String mode) {
        if (!"exam".equals(mode)) {
            return;
        }
        if ("postgrad".equals(stage)) {
            sb.append("task锚点：task1 重点检查写作目的、身份关系、语气、格式、要点覆盖；")
                    .append("task2 必须完成描述材料、解读含义、给出评论三个动作。\n");
            sb.append("降档规则：严重跑题不得高档；未完成关键任务时 task_achievement 不得高于 C；")
                    .append("字数严重不足时 task_achievement 与 content_quality 同时降档。\n");
            return;
        }
        sb.append("task锚点：先判断是否完成题目任务，再判断内容与语言质量；")
                .append("语言亮点不能抵消明显偏题或任务缺失。\n");
    }

    private String clip(String text, int maxLength) {
        String normalized = safe(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().replace('\n', ' ');
    }
}
