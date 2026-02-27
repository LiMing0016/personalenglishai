package com.personalenglishai.backend.ai.prompt;

import com.personalenglishai.backend.entity.UserAbilityProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class AbilityPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbilityPromptBuilder.class);

    private final boolean promptDebugEnabled;

    public AbilityPromptBuilder(@Value("${ai.prompt.debug:false}") boolean promptDebugEnabled) {
        this.promptDebugEnabled = promptDebugEnabled;
    }

    public String buildAbilityPrompt(UserAbilityProfile profile) {
        return buildAbilityPrompt(profile, null);
    }

    public String buildAbilityPrompt(UserAbilityProfile profile, Long userId) {
        if (profile == null) {
            return "";
        }

        int sampleCount = profile.getSampleCount() == null ? 0 : profile.getSampleCount();
        boolean gated = sampleCount <= 0;

        List<DimensionScore> scores = List.of(
                new DimensionScore("任务", safe(profile.getTaskScore())),
                new DimensionScore("连贯", safe(profile.getCoherenceScore())),
                new DimensionScore("语法", safe(profile.getGrammarScore())),
                new DimensionScore("词汇", safe(profile.getVocabularyScore())),
                new DimensionScore("结构", safe(profile.getStructureScore())),
                new DimensionScore("多样性", safe(profile.getVarietyScore()))
        );

        List<String> weaknessTop = gated
                ? List.of()
                : scores.stream()
                .sorted(Comparator.comparingDouble(DimensionScore::score))
                .limit(2)
                .map(DimensionScore::name)
                .collect(Collectors.toList());

        String stageName = stageName(profile.getStage());
        String levelHint = levelHint(safe(profile.getAssessedScore()));
        String stageGuidance = stageGuidance(profile.getStage());

        if (promptDebugEnabled) {
            log.info("[ABILITY_PROMPT] userId={} stage={} gated={} weaknessTop={}",
                    userId,
                    stageName,
                    gated,
                    weaknessTop.isEmpty() ? "" : String.join("/", weaknessTop));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(PromptTemplates.ABILITY_HEADER_V1).append('\n');
        sb.append("- 学段: ").append(stageName).append('\n');
        sb.append("- 综合分: ").append(formatScore(profile.getAssessedScore()))
                .append(" (internal level hint: ").append(levelHint).append(")").append('\n');
        sb.append("- 样本数: ").append(sampleCount).append('\n');

        if (gated) {
            sb.append("- 能力画像状态: 样本不足，先采用中性保守策略，优先给出清晰解释与基础可执行建议。\n");
        } else {
            sb.append("- 六维: ")
                    .append("任务=").append(formatScore(profile.getTaskScore())).append(", ")
                    .append("连贯=").append(formatScore(profile.getCoherenceScore())).append(", ")
                    .append("语法=").append(formatScore(profile.getGrammarScore())).append(", ")
                    .append("词汇=").append(formatScore(profile.getVocabularyScore())).append(", ")
                    .append("结构=").append(formatScore(profile.getStructureScore())).append(", ")
                    .append("多样性=").append(formatScore(profile.getVarietyScore())).append('\n');
            if (!weaknessTop.isEmpty()) {
                sb.append("- 弱项Top: ").append(String.join(" / ", weaknessTop)).append('\n');
            }
        }

        sb.append("- 学段指导: ").append(stageGuidance).append('\n');
        sb.append('\n').append(PromptTemplates.ABILITY_CONTROL_RULES_V1);
        return sb.toString();
    }

    private String formatScore(BigDecimal score) {
        return String.format(Locale.ROOT, "%.2f", safe(score));
    }

    private double safe(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }

    private String levelHint(double assessedScore) {
        if (assessedScore <= 39D) {
            return "A2";
        }
        if (assessedScore <= 59D) {
            return "B1";
        }
        if (assessedScore <= 74D) {
            return "B2";
        }
        return "C1";
    }

    private String stageName(Integer stage) {
        if (stage == null) {
            return "未知";
        }
        return switch (stage) {
            case 1 -> "高中";
            case 2 -> "四级";
            case 3 -> "六级";
            case 4 -> "考研";
            default -> "未知";
        };
    }

    private String stageGuidance(Integer stage) {
        if (stage == null) {
            return "按当前输入难度提供清晰、可执行的英语学习建议。";
        }
        return switch (stage) {
            case 1 -> "更基础，优先语法与常见句型，示例保持简单直观。";
            case 2 -> "偏应试与常见题型表达，强化常用连接词与段落衔接。";
            case 3 -> "更重逻辑与表达丰富度，允许适度复杂句式。";
            case 4 -> "更重论证结构与严谨表达、学术连接与观点推进。";
            default -> "按当前输入难度提供清晰、可执行的英语学习建议。";
        };
    }

    private record DimensionScore(String name, double score) {
    }
}
