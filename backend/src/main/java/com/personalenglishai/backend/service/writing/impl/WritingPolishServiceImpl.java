package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.PolishEssayRequest;
import com.personalenglishai.backend.dto.writing.PolishEssayResponse;
import com.personalenglishai.backend.dto.writing.PolishRequest;
import com.personalenglishai.backend.dto.writing.PolishResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WritingPolishServiceImpl implements WritingPolishService {

    private static final Logger log = LoggerFactory.getLogger(WritingPolishServiceImpl.class);
    private static final Map<String, String> TIER_INSTRUCTIONS = Map.of(
            "basic", "按当前评分 rubric 修正最明显的问题，优先保证切题、清晰和基本正确，目标接近 Band 3。",
            "steady", "按当前评分 rubric 做中等提升，优先补会拉低分数的关键短板，目标接近 Band 4。",
            "advanced", "按当前评分 rubric 做高强度优化，同时提升内容、结构和表达质量，目标接近高位 Band 4 或 Band 5。",
            "perfect", "按当前写作场景 rubric 的最高档特征优化，优先任务完成度、结构、词汇、语法和表达自然度，目标对齐 Band 5。"
    );
    private static final Map<String, Integer> RELEVANCE_RANK = Map.of(
            "fully_on_topic", 4,
            "mostly_on_topic", 3,
            "partially_off_topic", 2,
            "seriously_off_topic", 1
    );
    private static final Map<String, Integer> TASK_COMPLETION_RANK = Map.of(
            "fully_completed", 4,
            "mostly_completed", 3,
            "partially_completed", 2,
            "seriously_incomplete", 1
    );
    private static final Map<String, Integer> COVERAGE_RANK = Map.of(
            "all_key_points", 4,
            "most_key_points", 3,
            "partial_key_points", 2,
            "few_key_points", 1
    );

    private final OpenAiClient openAiClient;
    private final RubricService rubricService;
    private final RubricTextBuilder rubricTextBuilder;
    private final WritingEvaluateService writingEvaluateService;
    private final PolishRubricConfigService polishRubricConfigService;
    private final ObjectMapper objectMapper;

    public WritingPolishServiceImpl(OpenAiClient openAiClient,
                                    RubricService rubricService,
                                    RubricTextBuilder rubricTextBuilder,
                                    WritingEvaluateService writingEvaluateService,
                                    PolishRubricConfigService polishRubricConfigService,
                                    ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.rubricService = rubricService;
        this.rubricTextBuilder = rubricTextBuilder;
        this.writingEvaluateService = writingEvaluateService;
        this.polishRubricConfigService = polishRubricConfigService;
        this.objectMapper = objectMapper;
    }

    @Override
    public PolishResponse polish(PolishRequest request) {
        String tier = normalizeTier(request.getTier());
        String traceId = "polish-" + UUID.randomUUID().toString().substring(0, 8);
        String systemPrompt = """
                你是一位英语润色助手。请按当前档次要求润色句子，不要引用用户画像、能力等级或母语者表达偏好。

                润色档次要求: %s

                请提供 2 个不同的润色版本，每个版本用不同的改写思路。
                explanation 用中文简要说明改了什么、为什么这样改（30字以内）。

                只输出合法 JSON：
                {"candidates":[{"polished":"润色版本1","explanation":"中文解释1"},{"polished":"润色版本2","explanation":"中文解释2"}]}
                """.formatted(TIER_INSTRUCTIONS.getOrDefault(tier, TIER_INSTRUCTIONS.get("steady")));
        StringBuilder userPrompt = new StringBuilder("需润色的句子: ").append(request.getOriginal());
        if (request.getContext() != null && !request.getContext().isBlank()) {
            userPrompt.append("\n上下文: ").append(request.getContext());
        }
        if (request.getReason() != null && !request.getReason().isBlank()) {
            userPrompt.append("\n改进原因: ").append(request.getReason());
        }
        String rawResponse = openAiClient.callWithTraceId(systemPrompt, userPrompt.toString(), traceId);
        return parseSingleResponse(rawResponse, traceId);
    }

    @Override
    public PolishEssayResponse polishEssay(PolishEssayRequest request) {
        String tier = normalizeTier(request.getTier());
        String traceId = "polish-essay-" + UUID.randomUUID().toString().substring(0, 8);
        String stage = rubricService.normalizeStage(request.getStudyStage());
        String mode = rubricService.normalizeMode(request.getWritingMode());

        RubricActiveResponse activeRubric = rubricService.getActiveRubric(stage, mode);
        String rubricKey = activeRubric != null && activeRubric.getRubricKey() != null ? activeRubric.getRubricKey() : "unknown";
        String rubricText = rubricTextBuilder.buildRubricText(stage, mode);
        LevelMetadata levelMetadata = buildLevelMetadata(activeRubric);
        PolishRubricConfigService.PolishRubricProfile polishProfile = polishRubricConfigService.resolve(stage, mode, request.getTaskType());

        WritingEvaluateResponse baseline = evaluateEssayForPolish(request, request.getText(), stage, mode);
        String topicAlignmentStatus = resolveTopicAlignmentStatus(baseline);
        String baselineBand = resolveBandLabel(polishProfile, effectiveOverall(baseline));
        int sourceBandRank = bandRank(polishProfile, baselineBand);
        String route = resolveRoute(polishProfile, request, baseline, levelMetadata, topicAlignmentStatus);
        PolishRubricConfigService.TierProfile tierProfile = resolveTierProfile(polishProfile, tier);
        int targetBandRank = resolveTargetBandRank(tierProfile, sourceBandRank);

        String systemPrompt = buildEssaySystemPrompt(request, stage, mode, rubricKey, rubricText, polishProfile, tier, route, targetBandRank, baseline);
        String userPrompt = buildEssayUserPrompt(request, tier, route, sourceBandRank, targetBandRank, baseline);
        log.info("[POLISH-ESSAY] traceId={} userId={} tier={} stage={} mode={} rubricKey={} polishRubricKey={} route={} sourceBandRank={} targetBandRank={}",
                traceId, request.getUserId(), tier, stage, mode, rubricKey, polishProfile.getKey(), route, sourceBandRank, targetBandRank);

        String raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId, 0.5, 4096);
        ParsedEssayCandidate parsed = parseEssayCandidate(raw, traceId);
        String candidateEssay = sanitizeCandidateEssay(parsed.polishedEssay(), request.getText());
        WritingEvaluateResponse candidate = evaluateEssayForPolish(request, candidateEssay, stage, mode);

        boolean accepted = isSafeCandidate(request, request.getText(), candidateEssay, baseline, candidate);
        String finalBand = resolveBandLabel(polishProfile, effectiveOverall(candidate));
        int finalBandRank = bandRank(polishProfile, finalBand);
        boolean targetMet = accepted && finalBandRank <= targetBandRank;

        PolishEssayResponse response = new PolishEssayResponse();
        response.setRubricKey(rubricKey);
        response.setPolicyKey(resolvePolicyKey(candidate, baseline));
        response.setPolishRubricKey(polishProfile.getKey());
        response.setRoute(route);
        response.setProcessingModeLabel(processingModeLabel(route, mode));
        response.setTopicAlignmentStatus(resolveTopicAlignmentStatus(candidate));
        response.setRewriteMode(route);
        response.setBaselineBand(baselineBand);
        response.setBaselineScore(effectiveOverall(baseline));
        response.setBaselineGrades(copyGrades(baseline));
        response.setFinalBand(finalBand);
        response.setFinalScore(effectiveOverall(candidate));
        response.setFinalGrades(copyGrades(candidate));
        response.setSourceBandRank(sourceBandRank);
        response.setTargetBandRank(targetBandRank);
        response.setAccepted(accepted);
        response.setGuardTriggered(!accepted);
        response.setFallbackToOriginal(!accepted);
        response.setTargetMet(targetMet);
        response.setAttemptCount(1);
        response.setTargetTier(tier);
        response.setTargetGap(buildTargetGap(accepted, finalBand, targetBandRank));
        response.setBestEffort(accepted && !targetMet);
        response.setBaselineDirection(toDirectionSnapshot(baseline));
        response.setFinalDirection(toDirectionSnapshot(candidate));
        response.setBindingReason(resolveBindingReason(candidate));
        response.setUnmetCoreDimensions(resolveUnmetCoreDimensions(tier, tierProfile, candidate, levelMetadata));
        response.setSummary(parsed.summary());
        response.setPolishedEssay(candidateEssay);
        response.setSentences(List.of());
        return response;
    }

    private String buildEssaySystemPrompt(PolishEssayRequest request,
                                          String stage,
                                          String mode,
                                          String rubricKey,
                                          String rubricText,
                                          PolishRubricConfigService.PolishRubricProfile polishProfile,
                                          String tier,
                                          String route,
                                          int targetBandRank,
                                          WritingEvaluateResponse baseline) {
        return """
                你是一位英语作文润色助手。你的唯一目标是按当前写作场景的评分 rubric 安全提分。
                不要参考用户画像、能力等级或“母语者表达”偏好。
                必须优先保证切题、任务完成、结构清晰，再提升词汇和句式。

                [SCORING_CONTEXT]
                study_stage=%s
                writing_mode=%s
                rubric_key=%s
                task_type=%s
                policy_key=%s
                polish_rubric_key=%s
                tier=%s
                route=%s
                target_band_rank=%s

                [RUBRIC_FROM_DB]
                %s

                [ROUTE_RULE]
                %s

                [WORD_RANGE_RULE]
                %s

                [BASELINE]
                baseline_final_overall=%s
                baseline_direction=%s
                baseline_grades=%s

                输出要求：
                1. 只输出合法 JSON。
                2. polishedEssay 必须是一篇完整作文，不要拆成逐句数组。
                3. summary.strengths / summary.improvements 各给 2-3 条中文短句。
                4. 若 route=topic_correction_then_polish，优先保留原有段落结构，但纠正跑题内容。
                5. 若 route=corrected_rewrite，允许整篇重写，但必须严格对齐题目内容和写作要求。

                JSON schema:
                {"summary":{"strengths":["..."],"improvements":["..."]},"topicAlignmentStatus":"aligned|partial|off_topic","polishedEssay":"完整润色后作文"}
                """.formatted(
                stage,
                mode,
                rubricKey,
                safeText(request.getTaskType()),
                resolvePolicyKey(baseline, baseline),
                polishProfile.getKey(),
                tier,
                route,
                targetBandRank,
                rubricText,
                routeInstruction(route),
                buildWordRangeRule(request),
                effectiveOverall(baseline),
                directionSummary(baseline),
                copyGrades(baseline)
        );
    }

    private String buildEssayUserPrompt(PolishEssayRequest request,
                                        String tier,
                                        String route,
                                        int sourceBandRank,
                                        int targetBandRank,
                                        WritingEvaluateResponse baseline) {
        StringBuilder sb = new StringBuilder();
        sb.append("题目内容：\n").append(firstNonBlank(request.getTopicContent(), "未提供题目内容")).append("\n\n");
        sb.append("写作要求：\n").append(firstNonBlank(request.getTaskPrompt(), "未提供写作要求")).append("\n\n");
        sb.append("当前档次：").append(tier).append('\n');
        sb.append("当前路由：").append(route).append('\n');
        sb.append("原文档位：第 ").append(sourceBandRank).append(" 档\n");
        sb.append("目标档位：第 ").append(targetBandRank).append(" 档\n");
        sb.append("当前方向判断：").append(directionSummary(baseline)).append('\n');
        sb.append("字数要求：").append(buildWordRangeRule(request)).append("\n\n");
        sb.append("作文原文：\n").append(safeText(request.getText()));
        return sb.toString();
    }

    private ParsedEssayCandidate parseEssayCandidate(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            return new ParsedEssayCandidate(null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            PolishEssayResponse.Summary summary = null;
            JsonNode summaryNode = node.path("summary");
            if (summaryNode.isObject()) {
                summary = new PolishEssayResponse.Summary(readStringList(summaryNode.path("strengths")), readStringList(summaryNode.path("improvements")));
            }
            String polishedEssay = readText(node, "polishedEssay");
            if ((polishedEssay == null || polishedEssay.isBlank()) && node.path("sentences").isArray()) {
                List<String> pieces = new ArrayList<>();
                for (JsonNode sentenceNode : node.path("sentences")) {
                    String polished = readText(sentenceNode, "polished");
                    if (polished != null && !polished.isBlank()) {
                        pieces.add(polished.trim());
                    }
                }
                polishedEssay = String.join(" ", pieces).trim();
            }
            return new ParsedEssayCandidate(summary, polishedEssay);
        } catch (Exception e) {
            log.warn("[POLISH-ESSAY] traceId={} failed to parse AI response", traceId, e);
            return new ParsedEssayCandidate(null, null);
        }
    }

    private boolean isSafeCandidate(PolishEssayRequest request,
                                    String baselineEssay,
                                    String candidateEssay,
                                    WritingEvaluateResponse baseline,
                                    WritingEvaluateResponse candidate) {
        return !directionRegressed(baseline, candidate)
                && !capRegressed(baseline, candidate)
                && resolveWordCountBand(request, candidateEssay).rank() >= resolveWordCountBand(request, baselineEssay).rank()
                && effectiveOverall(candidate) >= effectiveOverall(baseline);
    }

    private boolean directionRegressed(WritingEvaluateResponse baseline, WritingEvaluateResponse candidate) {
        WritingEvaluateResponse.DirectionAssessmentDto before = directionOf(baseline);
        WritingEvaluateResponse.DirectionAssessmentDto after = directionOf(candidate);
        if (before == null || after == null) {
            return false;
        }
        return RELEVANCE_RANK.getOrDefault(after.getRelevance(), 0) < RELEVANCE_RANK.getOrDefault(before.getRelevance(), 0)
                || TASK_COMPLETION_RANK.getOrDefault(after.getTaskCompletion(), 0) < TASK_COMPLETION_RANK.getOrDefault(before.getTaskCompletion(), 0)
                || COVERAGE_RANK.getOrDefault(after.getCoverage(), 0) < COVERAGE_RANK.getOrDefault(before.getCoverage(), 0)
                || bandNo(after.getMaxBand()) < bandNo(before.getMaxBand());
    }

    private boolean capRegressed(WritingEvaluateResponse baseline, WritingEvaluateResponse candidate) {
        Integer before = baseline != null && baseline.getExamPolicy() != null ? baseline.getExamPolicy().getCapScore() : null;
        Integer after = candidate != null && candidate.getExamPolicy() != null ? candidate.getExamPolicy().getCapScore() : null;
        return before != null && after != null && after < before;
    }

    private String resolveRoute(PolishRubricConfigService.PolishRubricProfile profile,
                                PolishEssayRequest request,
                                WritingEvaluateResponse baseline,
                                LevelMetadata levelMetadata,
                                String topicAlignmentStatus) {
        if (!"exam".equalsIgnoreCase(request.getWritingMode())) {
            return "rubric_polish";
        }
        if (shouldForceRewrite(profile, request, baseline)) {
            return "corrected_rewrite";
        }
        PolishRubricConfigService.RouteRule routeRule = profile.getRouteRules().getOrDefault(topicAlignmentStatus, profile.getRouteRules().get("aligned"));
        if (routeRule == null) {
            return "rubric_polish";
        }
        if (routeRule.getDefaultRoute() != null && !routeRule.getDefaultRoute().isBlank()) {
            return routeRule.getDefaultRoute();
        }
        return canKeepFramework(routeRule.getKeepFrameworkIf(), baseline, levelMetadata)
                ? firstNonBlank(routeRule.getRouteIfKeepFramework(), "topic_correction_then_polish")
                : firstNonBlank(routeRule.getRouteIfNotKeepFramework(), "corrected_rewrite");
    }

    private boolean shouldForceRewrite(PolishRubricConfigService.PolishRubricProfile profile,
                                       PolishEssayRequest request,
                                       WritingEvaluateResponse baseline) {
        PolishRubricConfigService.ForceRewriteRule forceRewrite = profile.getForceRewrite();
        if (forceRewrite == null) {
            return false;
        }
        if (forceRewrite.getRewriteUnderWordRatio() != null && wordRatio(request, request.getText()) < forceRewrite.getRewriteUnderWordRatio()) {
            return true;
        }
        WritingEvaluateResponse.DirectionAssessmentDto direction = directionOf(baseline);
        if (direction == null) {
            return false;
        }
        if (Boolean.TRUE.equals(forceRewrite.getMissingTaskActions())
                && ("seriously_incomplete".equals(direction.getTaskCompletion()) || "few_key_points".equals(direction.getCoverage()))) {
            return true;
        }
        if (Boolean.TRUE.equals(forceRewrite.getMissingCoreMaterialCoverage())
                && "task2".equalsIgnoreCase(safeText(request.getTaskType()))
                && ("few_key_points".equals(direction.getCoverage()) || "seriously_off_topic".equals(direction.getRelevance()))) {
            return true;
        }
        return Boolean.TRUE.equals(forceRewrite.getSevereFormatMismatch())
                && "task1".equalsIgnoreCase(safeText(request.getTaskType()))
                && "seriously_incomplete".equals(direction.getTaskCompletion());
    }

    private boolean canKeepFramework(PolishRubricConfigService.KeepFrameworkRule rule,
                                     WritingEvaluateResponse baseline,
                                     LevelMetadata metadata) {
        if (rule == null || baseline == null || baseline.getGrades() == null) {
            return false;
        }
        return meetsLevel("structure", baseline.getGrades().get("structure"), rule.getStructureMinLevel(), metadata)
                && meetsLevel("grammar", baseline.getGrades().get("grammar"), rule.getGrammarMinLevel(), metadata)
                && meetsLevel("expression", baseline.getGrades().get("expression"), rule.getExpressionMinLevel(), metadata);
    }

    private boolean meetsLevel(String dimensionKey, String actualLevel, String requiredLevel, LevelMetadata metadata) {
        if (requiredLevel == null || requiredLevel.isBlank()) {
            return true;
        }
        return metadata.scoreFor(dimensionKey, actualLevel) >= metadata.scoreFor(dimensionKey, requiredLevel);
    }

    private PolishRubricConfigService.TierProfile resolveTierProfile(PolishRubricConfigService.PolishRubricProfile profile, String tier) {
        return profile.getTierProfiles().getOrDefault(tier, profile.getTierProfiles().get("steady"));
    }

    private int resolveTargetBandRank(PolishRubricConfigService.TierProfile tierProfile, int sourceBandRank) {
        if (tierProfile == null || tierProfile.getTargetBandRankBySource() == null) {
            return sourceBandRank;
        }
        return tierProfile.getTargetBandRankBySource().getOrDefault(String.valueOf(sourceBandRank), sourceBandRank);
    }

    private List<String> resolveUnmetCoreDimensions(String tier,
                                                    PolishRubricConfigService.TierProfile tierProfile,
                                                    WritingEvaluateResponse evaluation,
                                                    LevelMetadata metadata) {
        if (tierProfile == null || tierProfile.getCoreDimensions() == null || evaluation == null || evaluation.getGrades() == null) {
            return List.of();
        }
        List<String> levels = metadata.globalLevels();
        if (levels.isEmpty()) {
            return List.of();
        }
        String requiredLevel = switch (tier) {
            case "perfect" -> levels.get(0);
            case "advanced" -> levels.get(Math.min(1, levels.size() - 1));
            case "steady" -> levels.get(Math.min(2, levels.size() - 1));
            default -> null;
        };
        if (requiredLevel == null) {
            return List.of();
        }
        List<String> unmet = new ArrayList<>();
        for (String dimension : tierProfile.getCoreDimensions()) {
            if (!meetsLevel(dimension, evaluation.getGrades().get(dimension), requiredLevel, metadata)) {
                unmet.add(dimension);
            }
        }
        return unmet;
    }

    private WritingEvaluateResponse evaluateEssayForPolish(PolishEssayRequest request, String essayText, String stage, String mode) {
        return evaluateEssayForPolish(
                essayText,
                stage,
                mode,
                request.getTopicContent(),
                request.getTaskPrompt(),
                request.getTaskType(),
                request.getMinWords(),
                request.getRecommendedMaxWords(),
                null,
                request.getUserId()
        );
    }

    private WritingEvaluateResponse evaluateEssayForPolish(String essayText,
                                                           String stage,
                                                           String mode,
                                                           String topicContent,
                                                           String taskPrompt,
                                                           String taskType,
                                                           Integer minWords,
                                                           Integer recommendedMaxWords,
                                                           Integer maxScore,
                                                           Long userId) {
        WritingEvaluateRequest evalRequest = new WritingEvaluateRequest();
        evalRequest.setEssay(essayText);
        evalRequest.setMode(mode);
        evalRequest.setLang("en");
        evalRequest.setTaskPrompt(taskPrompt);
        evalRequest.setStudyStage(stage);
        evalRequest.setTopicTitle(firstNonBlank(topicContent, null));
        evalRequest.setTaskType(taskType);
        evalRequest.setMinWords(minWords);
        evalRequest.setRecommendedMaxWords(recommendedMaxWords);
        evalRequest.setMaxScore(maxScore);
        evalRequest.setUserId(userId);
        return writingEvaluateService.evaluateForPolish(evalRequest);
    }

    private String resolveTopicAlignmentStatus(WritingEvaluateResponse evaluation) {
        WritingEvaluateResponse.DirectionAssessmentDto direction = directionOf(evaluation);
        if (direction == null) return "aligned";
        if ("seriously_off_topic".equals(direction.getRelevance())) return "off_topic";
        if ("partially_off_topic".equals(direction.getRelevance())
                || "partially_completed".equals(direction.getTaskCompletion())
                || "seriously_incomplete".equals(direction.getTaskCompletion())
                || "partial_key_points".equals(direction.getCoverage())
                || "few_key_points".equals(direction.getCoverage())) return "partial";
        return "aligned";
    }

    private String resolveBindingReason(WritingEvaluateResponse evaluation) {
        WritingEvaluateResponse.DirectionAssessmentDto direction = directionOf(evaluation);
        if (direction != null) {
            if ("seriously_off_topic".equals(direction.getRelevance()) || "partially_off_topic".equals(direction.getRelevance())) return "topic_cap";
            if ("seriously_incomplete".equals(direction.getTaskCompletion()) || "partially_completed".equals(direction.getTaskCompletion())) return "task_completion_cap";
            if ("few_key_points".equals(direction.getCoverage()) || "partial_key_points".equals(direction.getCoverage())) return "coverage_cap";
        }
        if (evaluation != null && evaluation.getExamPolicy() != null && evaluation.getExamPolicy().getFlags() != null) {
            Map<String, Boolean> flags = evaluation.getExamPolicy().getFlags();
            if (Boolean.TRUE.equals(flags.get("light_under_word_count"))
                    || Boolean.TRUE.equals(flags.get("moderate_under_word_count"))
                    || Boolean.TRUE.equals(flags.get("severe_under_word_count"))
                    || Boolean.TRUE.equals(flags.get("extreme_under_word_count"))) {
                return "word_count_cap";
            }
        }
        return null;
    }

    private String resolvePolicyKey(WritingEvaluateResponse preferred, WritingEvaluateResponse fallback) {
        if (preferred != null && preferred.getExamPolicy() != null && preferred.getExamPolicy().getPolicyKey() != null) {
            return preferred.getExamPolicy().getPolicyKey();
        }
        return fallback != null && fallback.getExamPolicy() != null ? fallback.getExamPolicy().getPolicyKey() : null;
    }

    private String resolveBandLabel(PolishRubricConfigService.PolishRubricProfile profile, int score) {
        return profile.getBandScoreFloor().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .filter(entry -> score >= entry.getValue())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("Band 1");
    }

    private int bandRank(PolishRubricConfigService.PolishRubricProfile profile, String bandLabel) {
        return profile.getBandRankMap().getOrDefault(bandLabel, 5);
    }

    private int bandNo(String bandLabel) {
        String digits = bandLabel == null ? "" : bandLabel.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private String buildTargetGap(boolean accepted, String finalBand, int targetBandRank) {
        if (!accepted) return "候选稿未通过安全复评，已回退到原文";
        return bandNo(finalBand) <= targetBandRank ? null : "当前 " + finalBand + "，目标第 " + targetBandRank + " 档";
    }

    private String buildWordRangeRule(PolishEssayRequest request) {
        Integer minWords = request.getMinWords();
        Integer recommendedMaxWords = request.getRecommendedMaxWords();
        if (recommendedMaxWords != null && recommendedMaxWords > 0) {
            int maxTarget = (int) Math.floor(recommendedMaxWords * 1.15d);
            if (minWords != null && minWords > 0) return "至少 " + minWords + " 词，推荐上限 " + recommendedMaxWords + " 词，整篇尽量控制在 " + recommendedMaxWords + "-" + maxTarget + " 词。";
            return "推荐上限 " + recommendedMaxWords + " 词，整篇尽量控制在 " + recommendedMaxWords + "-" + maxTarget + " 词。";
        }
        if (minWords != null && minWords > 0) return "至少 " + minWords + " 词。";
        return "未配置字数要求。";
    }

    private String routeInstruction(String route) {
        return switch (route) {
            case "topic_correction_then_polish" -> "优先保留段落框架和论证顺序，重点纠正偏题内容、补齐题目要求中的动作和信息。";
            case "corrected_rewrite" -> "不要受原句限制，直接按题目内容、写作要求和当前档次目标生成完整作文。";
            default -> "在原文基础上安全提分，尽量保留原有内容和结构。";
        };
    }

    private String processingModeLabel(String route, String mode) {
        if (!"exam".equalsIgnoreCase(mode)) {
            return switch (route) {
                case "corrected_rewrite" -> "本次处理：已按你的目标重构整篇";
                default -> "本次处理：按当前目标润色";
            };
        }
        return switch (route) {
            case "topic_correction_then_polish" -> "本次处理：先纠偏再润色";
            case "corrected_rewrite" -> "本次处理：原文存在明显偏题，已按题目要求重写";
            default -> "本次处理：正常润色";
        };
    }

    private WritingEvaluateResponse.DirectionAssessmentDto directionOf(WritingEvaluateResponse evaluation) {
        return evaluation != null && evaluation.getExamPolicy() != null ? evaluation.getExamPolicy().getDirectionAssessment() : null;
    }

    private PolishEssayResponse.DirectionSnapshot toDirectionSnapshot(WritingEvaluateResponse evaluation) {
        WritingEvaluateResponse.DirectionAssessmentDto direction = directionOf(evaluation);
        return direction == null ? null : new PolishEssayResponse.DirectionSnapshot(direction.getRelevance(), direction.getTaskCompletion(), direction.getCoverage(), direction.getMaxBand());
    }

    private String directionSummary(WritingEvaluateResponse evaluation) {
        PolishEssayResponse.DirectionSnapshot snapshot = toDirectionSnapshot(evaluation);
        if (snapshot == null) return "not_available";
        return "relevance=" + snapshot.getRelevance()
                + ", taskCompletion=" + snapshot.getTaskCompletion()
                + ", coverage=" + snapshot.getCoverage()
                + ", maxBand=" + snapshot.getMaxBand();
    }

    private int effectiveOverall(WritingEvaluateResponse evaluation) {
        if (evaluation != null && evaluation.getExamPolicy() != null && evaluation.getExamPolicy().getFinalOverall() != null) {
            return evaluation.getExamPolicy().getFinalOverall();
        }
        return evaluation != null && evaluation.getScore() != null && evaluation.getScore().getOverall() != null ? evaluation.getScore().getOverall() : 0;
    }

    private double wordRatio(PolishEssayRequest request, String essayText) {
        if (request.getMinWords() == null || request.getMinWords() <= 0) return 1.0d;
        return (double) WritingScoreUtils.countWords(safeText(essayText)) / request.getMinWords();
    }

    private double wordRatio(Integer minWords, String essayText) {
        if (minWords == null || minWords <= 0) return 1.0d;
        return (double) WritingScoreUtils.countWords(safeText(essayText)) / minWords;
    }

    private WordCountBand resolveWordCountBand(PolishEssayRequest request, String essayText) {
        if (request.getMinWords() == null || request.getMinWords() <= 0) return new WordCountBand("not_limited", 4);
        double ratio = wordRatio(request, essayText) + 0.02d;
        if (ratio >= 1.0d) return new WordCountBand("fully_completed", 4);
        if (ratio >= 0.85d) return new WordCountBand("mostly_completed", 3);
        if (ratio >= 0.70d) return new WordCountBand("partially_completed", 2);
        return new WordCountBand("seriously_incomplete", 1);
    }

    private String normalizeTier(String tier) {
        if (tier == null) return "steady";
        String normalized = tier.trim().toLowerCase(Locale.ROOT);
        return TIER_INSTRUCTIONS.containsKey(normalized) ? normalized : "steady";
    }

    private String stripCodeFences(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    private PolishResponse parseSingleResponse(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[POLISH] traceId={} empty response from AI", traceId);
            return new PolishResponse(null, "AI 未返回有效结果");
        }
        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            JsonNode candidatesNode = node.path("candidates");
            if (candidatesNode.isArray() && !candidatesNode.isEmpty()) {
                List<PolishResponse.Candidate> candidates = new ArrayList<>();
                for (JsonNode candidateNode : candidatesNode) {
                    String polished = candidateNode.path("polished").asText(null);
                    String explanation = candidateNode.path("explanation").asText(null);
                    if (polished != null && !polished.isBlank()) {
                        candidates.add(new PolishResponse.Candidate(polished, explanation));
                    }
                }
                if (!candidates.isEmpty()) {
                    return new PolishResponse(candidates);
                }
            }
            return new PolishResponse(node.path("polished").asText(null), node.path("explanation").asText(null));
        } catch (Exception e) {
            log.warn("[POLISH] traceId={} failed to parse AI response: {}", traceId, raw, e);
            return new PolishResponse(null, "AI 返回格式异常，请重试");
        }
    }

    private Map<String, String> copyGrades(WritingEvaluateResponse evaluation) {
        return evaluation == null || evaluation.getGrades() == null ? Map.of() : new LinkedHashMap<>(evaluation.getGrades());
    }

    private String sanitizeCandidateEssay(String candidateEssay, String originalEssay) {
        String normalized = safeText(candidateEssay);
        return normalized.isBlank() ? safeText(originalEssay) : normalized;
    }

    private String readText(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText(null);
            if (text != null && !text.isBlank()) values.add(text.trim());
        }
        return values;
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.trim().isBlank() ? preferred.trim() : fallback;
    }

    private LevelMetadata buildLevelMetadata(RubricActiveResponse activeRubric) {
        Map<String, Map<String, Integer>> scoreMap = new LinkedHashMap<>();
        List<String> globalLevels = new ArrayList<>();
        if (activeRubric != null && activeRubric.getDimensions() != null) {
            for (RubricActiveResponse.DimensionDto dimension : activeRubric.getDimensions()) {
                Map<String, Integer> levelScores = new LinkedHashMap<>();
                if (dimension.getLevels() != null) {
                    dimension.getLevels().stream()
                            .sorted(Comparator.comparingInt((RubricActiveResponse.LevelDto level) -> level.getScore() == null ? 0 : level.getScore()).reversed())
                            .forEach(level -> {
                                String key = safeText(level.getLevel());
                                levelScores.put(key, level.getScore() == null ? 0 : level.getScore());
                                if (!globalLevels.contains(key)) globalLevels.add(key);
                            });
                }
                scoreMap.put(dimension.getDimensionKey(), levelScores);
            }
        }
        return new LevelMetadata(scoreMap, globalLevels);
    }

    private record ParsedEssayCandidate(PolishEssayResponse.Summary summary, String polishedEssay) {}
    private record WordCountBand(String label, int rank) {}
    private record LevelMetadata(Map<String, Map<String, Integer>> scoreMap, List<String> globalLevels) {
        int scoreFor(String dimensionKey, String level) {
            if (dimensionKey == null || level == null) return 0;
            Map<String, Integer> dimensionScores = scoreMap.get(dimensionKey);
            return dimensionScores == null ? 0 : dimensionScores.getOrDefault(level.trim(), 0);
        }
    }
}
