package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WritingExamPolicyService {

    private static final String POSTGRAD_STAGE = "postgrad";
    private static final String EXAM_MODE = "exam";
    private static final String POLICY_KEY = "postgrad-exam-policy-v1";
    private static final double BUFFER_ZONE = 0.02d;

    public ExamPolicyResult evaluate(
            String effectiveStage,
            String mode,
            String taskType,
            WritingEvaluateRequest request,
            Map<String, Integer> scoreByDimension,
            List<WritingEvaluateResponse.ErrorDto> errors
    ) {
        int rawOverall = computeRawOverall(effectiveStage, mode, taskType, scoreByDimension);
        if (!supports(effectiveStage, mode)) {
            return new ExamPolicyResult(null, rawOverall, rawOverall, null, 0, Map.of(), List.of(), null);
        }

        TextStats textStats = buildTextStats(request != null ? request.getEssay() : null);
        DirectionAssessment direction = buildDirectionAssessment(request, scoreByDimension, textStats);
        WordCountAdjustment wordCountAdjustment = buildWordCountAdjustment(request, textStats);

        Integer capScore = minCap(direction.capScore(), wordCountAdjustment.capScore());
        int cappedScore = capScore == null ? rawOverall : Math.min(rawOverall, capScore);
        int finalOverall = Math.max(0, cappedScore - wordCountAdjustment.deduction());

        Map<String, Boolean> flags = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();

        if (direction.capScore() != null) {
            flags.put("direction_limited", true);
            reasons.addAll(direction.reasons());
        }
        if (wordCountAdjustment.capScore() != null || wordCountAdjustment.deduction() > 0) {
            flags.put(wordCountAdjustment.flagKey(), true);
            reasons.add(wordCountAdjustment.reason());
        }
        if (errors != null && !errors.isEmpty()) {
            long majorCount = errors.stream()
                    .filter(error -> error != null && "major".equalsIgnoreCase(error.getSeverity()))
                    .count();
            if (majorCount > 0) {
                flags.put("major_errors_present", true);
                reasons.add("本次结果已保留重大错误统计，但未额外叠加重大错误扣分。");
            }
        }

        return new ExamPolicyResult(
                POLICY_KEY,
                rawOverall,
                finalOverall,
                capScore,
                wordCountAdjustment.deduction(),
                flags,
                reasons,
                direction
        );
    }

    public boolean supports(String stage, String mode) {
        return POSTGRAD_STAGE.equals(normalize(stage)) && EXAM_MODE.equals(normalize(mode));
    }

    private int computeRawOverall(String stage, String mode, String taskType, Map<String, Integer> scoreByDimension) {
        if (scoreByDimension == null || scoreByDimension.isEmpty()) {
            return 60;
        }
        if (!supports(stage, mode)) {
            return average(scoreByDimension);
        }
        Map<String, Double> weights = resolveWeights(taskType);
        double total = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            total += scoreByDimension.getOrDefault(entry.getKey(), 60) * entry.getValue();
        }
        return (int) Math.round(total);
    }

    private Map<String, Double> resolveWeights(String taskType) {
        Map<String, Double> weights = new LinkedHashMap<>();
        String normalizedTaskType = normalize(taskType);
        if ("task1".equals(normalizedTaskType)) {
            weights.put("task_achievement", 0.35d);
            weights.put("content_quality", 0.20d);
        } else {
            weights.put("task_achievement", 0.30d);
            weights.put("content_quality", 0.25d);
        }
        weights.put("structure", 0.15d);
        weights.put("vocabulary", 0.10d);
        weights.put("grammar", 0.10d);
        weights.put("expression", 0.10d);
        return weights;
    }

    private DirectionAssessment buildDirectionAssessment(
            WritingEvaluateRequest request,
            Map<String, Integer> scoreByDimension,
            TextStats textStats
    ) {
        int taskScore = scoreByDimension != null && scoreByDimension.get("task_achievement") != null
                ? scoreByDimension.get("task_achievement")
                : scoreByDimension != null && scoreByDimension.get("content_quality") != null
                ? scoreByDimension.get("content_quality")
                : 60;

        double ratio = request != null && request.getMinWords() != null && request.getMinWords() > 0
                ? (double) textStats.wordCount() / request.getMinWords()
                : -1;
        double effectiveRatio = ratio >= 0 ? ratio + BUFFER_ZONE : -1;

        String relevance = taskScore >= 85 ? "fully_on_topic"
                : taskScore >= 70 ? "mostly_on_topic"
                : taskScore >= 55 ? "partially_off_topic"
                : "seriously_off_topic";

        String coverage = taskScore >= 85 ? "all_key_points"
                : taskScore >= 70 ? "most_key_points"
                : taskScore >= 55 ? "partial_key_points"
                : "few_key_points";

        String taskCompletion;
        if (effectiveRatio >= 1.0d) {
            taskCompletion = "fully_completed";
        } else if (effectiveRatio >= 0.85d) {
            taskCompletion = "mostly_completed";
        } else if (effectiveRatio >= 0.70d) {
            taskCompletion = "partially_completed";
        } else if (effectiveRatio >= 0.0d) {
            taskCompletion = "seriously_incomplete";
        } else {
            taskCompletion = taskScore >= 85 ? "fully_completed"
                    : taskScore >= 70 ? "mostly_completed"
                    : taskScore >= 55 ? "partially_completed"
                    : "seriously_incomplete";
        }

        Integer topicCap = switch (relevance) {
            case "partially_off_topic" -> 54;
            case "seriously_off_topic" -> 39;
            default -> null;
        };
        Integer taskCompletionCap = switch (taskCompletion) {
            case "partially_completed" -> 69;
            case "seriously_incomplete" -> 54;
            default -> null;
        };
        Integer coverageCap = switch (coverage) {
            case "partial_key_points" -> 69;
            case "few_key_points" -> 54;
            default -> null;
        };

        Integer capScore = minCap(minCap(topicCap, taskCompletionCap), coverageCap);
        int maxBandNo = capScore == null ? 5 : bandNoForScore(capScore);
        List<String> reasons = new ArrayList<>();
        if (topicCap != null) {
            reasons.add(switch (relevance) {
                case "partially_off_topic" -> "存在一定偏题风险，最高不超过 Band 2";
                default -> "存在明显跑题风险，最高不超过 Band 1";
            });
        }
        if (taskCompletionCap != null) {
            reasons.add(switch (taskCompletion) {
                case "partially_completed" -> "任务完成不完整，最高不超过 Band 3";
                default -> "任务明显未完成，最高不超过 Band 2";
            });
        }
        if (coverageCap != null) {
            reasons.add(switch (coverage) {
                case "partial_key_points" -> "要点覆盖不完整，最高不超过 Band 3";
                default -> "要点覆盖较少，最高不超过 Band 2";
            });
        }

        return new DirectionAssessment(relevance, taskCompletion, coverage, bandLabel(maxBandNo), capScore, reasons);
    }

    private WordCountAdjustment buildWordCountAdjustment(WritingEvaluateRequest request, TextStats textStats) {
        if (request == null || request.getMinWords() == null || request.getMinWords() <= 0) {
            return new WordCountAdjustment(null, 0, "word_count_policy_skipped", "未配置最低词数，跳过字数处罚。");
        }
        double ratio = (double) textStats.wordCount() / request.getMinWords();
        double effectiveRatio = ratio + BUFFER_ZONE;
        if (effectiveRatio >= 1.0d) {
            return new WordCountAdjustment(null, 0, "word_count_ok", "字数达标，不触发处罚。");
        }
        if (effectiveRatio >= 0.85d) {
            return new WordCountAdjustment(84, 3, "light_under_word_count", "字数轻微不足，最高不超过 Band 4，并扣 3 分。");
        }
        if (effectiveRatio >= 0.70d) {
            return new WordCountAdjustment(69, 5, "moderate_under_word_count", "字数中度不足，最高不超过 Band 3，并扣 5 分。");
        }
        if (effectiveRatio >= 0.50d) {
            return new WordCountAdjustment(54, 0, "severe_under_word_count", "字数严重不足，最高不超过 Band 2。");
        }
        return new WordCountAdjustment(39, 0, "extreme_under_word_count", "字数极严重不足，最高不超过 Band 1。");
    }

    private TextStats buildTextStats(String essay) {
        String text = essay == null ? "" : essay.trim();
        if (text.isEmpty()) {
            return new TextStats(0);
        }
        return new TextStats(text.split("\\s+").length);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int average(Map<String, Integer> scoreByDimension) {
        int total = 0;
        for (Integer value : scoreByDimension.values()) {
            total += value == null ? 60 : value;
        }
        return Math.round(total / (float) scoreByDimension.size());
    }

    private Integer minCap(Integer current, Integer next) {
        if (current == null) {
            return next;
        }
        if (next == null) {
            return current;
        }
        return Math.min(current, next);
    }

    private int bandNoForScore(int score) {
        if (score >= 85) return 5;
        if (score >= 70) return 4;
        if (score >= 55) return 3;
        if (score >= 40) return 2;
        return 1;
    }

    private String bandLabel(int bandNo) {
        return "Band " + Math.max(1, Math.min(5, bandNo));
    }

    public record ExamPolicyResult(
            String policyKey,
            int rawOverall,
            int finalOverall,
            Integer capScore,
            int deductionTotal,
            Map<String, Boolean> flags,
            List<String> reasons,
            DirectionAssessment directionAssessment
    ) {}

    public record DirectionAssessment(
            String relevance,
            String taskCompletion,
            String coverage,
            String maxBand,
            Integer capScore,
            List<String> reasons
    ) {}

    private record WordCountAdjustment(Integer capScore, int deduction, String flagKey, String reason) {}
    private record TextStats(int wordCount) {}
}
