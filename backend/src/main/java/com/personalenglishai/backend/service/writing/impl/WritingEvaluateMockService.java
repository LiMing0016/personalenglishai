package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesException;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextRequest;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import com.personalenglishai.backend.service.writing.GrammarCheckService;
import com.personalenglishai.backend.service.writing.TrustedRewriteService;
import com.personalenglishai.backend.service.writing.WritingEvaluateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class WritingEvaluateMockService implements WritingEvaluateService {

    private static final Logger log = LoggerFactory.getLogger(WritingEvaluateMockService.class);
    private static final String DEFAULT_STAGE = "highschool";
    private static final String DEFAULT_LEVEL = "C";


    // ----------------------------------------------------------------
    // System prompt
    // ----------------------------------------------------------------
    private static final String SYSTEM_PROMPT = """
            你是一位严格的英语写作评分老师。
            必须严格依据输入中的评分标准逐维度评分，若其他说明与评分标准冲突，以评分标准为准。
            必须先判任务完成度与切题性，再判内容与语言质量。
            若作文偏题、未完成任务或材料未解读，不得因语言较好给高分。
            不要生成逐条语法错误清单；errors 由外部语法检查链路提供。
            只输出合法 JSON。
            """;

    // ----------------------------------------------------------------
    // Chinese-learner high-frequency error patterns
    // ----------------------------------------------------------------
    private static final String CHINESE_LEARNER_ERRORS = """
            [中国学生常见英文写作错误模式]
            以下模式仅作为评分参考提醒，可在相关维度分析中酌情体现：
            1. 中式英语/直译：如 "I very like"、"have a good time to do sth"、very 过度使用
            2. 时态不一致：同一段落中混用过去时和现在时
            3. 主谓一致错误：如 "Everyone have"、"The number of students are"、"Each of them are"
            4. 弱动词/形容词过度使用：过度依赖 get/have/make/do/feel/good/bad/big/small/very
            5. 连接词滥用：机械重复 "Firstly... Secondly... Thirdly... Fourthly..."
            6. 格式问题（考试/功能写作）：缺少称呼、结尾、落款，或段落格式失当
            7. 介词/搭配错误：如 "arrive to"、"good at to do"、"depend of"
            """;

    // ----------------------------------------------------------------
    // Few-shot examples
    // ----------------------------------------------------------------
    private static final String DEFAULT_FEW_SHOT_EXAMPLE = """
            [评分示例 — 仅供参考格式和尺度，不要照搬内容]
            输入作文（85词）:"Last weekend I go to the park with my friends. We have a very good time there. The weather is sunny and warm. We played football and flied kites. I think outdoor activities is very important for students. Firstly it can make us healthy. Secondly it can help us relax. In my opinion we should do more exercise in our daily life. I hope everyone can join us next time."

            评分输出：
            {
              "mode":"free",
              "grades":{"content_quality":"B","structure":"C","vocabulary":"C","grammar":"C","expression":"C"},
              "priority_focus":{"dimension":"grammar","reason":"时态错误和主谓不一致贯穿全文，是当前最影响表达准确性的问题。","action_item":"今天用 5 分钟把作文中所有动词标出来，逐个检查时态是否统一为过去时。"},
              "summary":"亮点是中心论点明确；核心问题是时态混乱与主谓一致错误。"
            }
            """;

    private final RubricService rubricService;
    private final RubricTextBuilder rubricTextBuilder;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final UserAbilityProfileMapper abilityProfileMapper;
    private final WritingEvaluationPersistenceService writingEvaluationPersistenceService;
    private final WritingExamPolicyService writingExamPolicyService;
    private final GrammarCheckService grammarCheckService;
    private final TrustedRewriteService trustedRewriteService;
    private final DocumentService documentService;
    private final DefaultScorePromptContextResolver scorePromptContextResolver;
    private final DefaultScorePromptCacheKeyBuilder scorePromptCacheKeyBuilder;
    private final RedisScoreRuntimeStateService runtimeStateService;
    private final ScoreStagePromptPolicyRegistry scoreStagePromptPolicyRegistry;
    private final ConcurrentMap<String, String> promptPrefixCache = new ConcurrentHashMap<>();

    public WritingEvaluateMockService(
            RubricService rubricService,
            RubricTextBuilder rubricTextBuilder,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper,
            UserAbilityProfileMapper abilityProfileMapper,
            WritingEvaluationPersistenceService writingEvaluationPersistenceService,
            WritingExamPolicyService writingExamPolicyService,
            GrammarCheckService grammarCheckService,
            TrustedRewriteService trustedRewriteService,
            DocumentService documentService,
            DefaultScorePromptContextResolver scorePromptContextResolver,
            DefaultScorePromptCacheKeyBuilder scorePromptCacheKeyBuilder,
            RedisScoreRuntimeStateService runtimeStateService,
            ScoreStagePromptPolicyRegistry scoreStagePromptPolicyRegistry
    ) {
        this.rubricService = rubricService;
        this.rubricTextBuilder = rubricTextBuilder;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.abilityProfileMapper = abilityProfileMapper;
        this.writingEvaluationPersistenceService = writingEvaluationPersistenceService;
        this.writingExamPolicyService = writingExamPolicyService;
        this.grammarCheckService = grammarCheckService;
        this.trustedRewriteService = trustedRewriteService;
        this.documentService = documentService;
        this.scorePromptContextResolver = scorePromptContextResolver;
        this.scorePromptCacheKeyBuilder = scorePromptCacheKeyBuilder;
        this.runtimeStateService = runtimeStateService;
        this.scoreStagePromptPolicyRegistry = scoreStagePromptPolicyRegistry;
    }

    // ================================================================
    // Main entry
    // ================================================================

    @Override
    public WritingEvaluateResponse evaluate(WritingEvaluateRequest request) {
        return evaluateInternal(request, true);
    }

    @Override
    public WritingEvaluateResponse evaluateForPolish(WritingEvaluateRequest request) {
        return evaluateInternal(request, false);
    }

    private WritingEvaluateResponse evaluateInternal(WritingEvaluateRequest request, boolean persistSideEffects) {
        String requestId = "eval-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        WritingSessionMetadataResponse sessionMetadata = loadSessionMetadata(request);
        String requestedStage = rubricService.normalizeStage(firstNonBlank(
                sessionMetadata == null ? null : sessionMetadata.getStudyStage(),
                request.getStudyStage()
        ));
        String mode = resolveModeForRequest(requestedStage, firstNonBlank(
                sessionMetadata == null ? null : sessionMetadata.getMode(),
                request.getMode()
        ));
        String taskType = normalizeTaskType(firstNonBlank(
                sessionMetadata == null ? null : sessionMetadata.getTaskType(),
                request.getTaskType()
        ));
        String effectiveStage = requestedStage;
        RubricActiveResponse rubric = rubricService.getActiveRubric(effectiveStage, mode);
        if (rubric == null || rubric.getDimensions() == null || rubric.getDimensions().isEmpty()) {
            effectiveStage = DEFAULT_STAGE;
            rubric = rubricService.getActiveRubric(effectiveStage, mode);
        }

        if (rubric == null || rubric.getDimensions() == null || rubric.getDimensions().isEmpty()) {
            log.warn("Rubric not found. requestId={} requestedStage={} effectiveStage={} mode={}", requestId, requestedStage, effectiveStage, mode);
            return buildLegacyFallback(requestId);
        }

        try {
            // Read existing profile BEFORE update so we can compare progress
            UserAbilityProfile existingProfile = persistSideEffects ? readProfileQuietly(request.getUserId()) : null;

            String rubricText = rubricTextBuilder.buildRubricText(effectiveStage, mode);
            if (rubricText.isBlank()) {
                rubricText = buildFallbackRubricText(rubric, effectiveStage, mode);
            }
            String renderedRubricHash = sha256(rubricText);
            String promptVersion = "score-v1";
            String requestProvider = request.getAiProvider();
            String modelName = openAiClient.resolveModel(requestProvider);
            ScorePromptContext promptContext = scorePromptContextResolver.resolve(
                    request,
                    modelName,
                    promptVersion,
                    rubric.getRubricKey(),
                    renderedRubricHash
            );
            String promptCacheKey = scorePromptCacheKeyBuilder.build(promptContext);
            String systemPrompt = buildSystemPrompt(effectiveStage, mode, taskType);
            String promptPrefix = buildCachedPromptPrefix(promptContext, rubricText);
            String promptSuffix = buildDynamicPromptSuffix(request, promptContext, mode, effectiveStage, taskType);
            String instructionsHash = computePromptSegmentHash(systemPrompt);
            String cachedPrefixHash = computePromptSegmentHash(promptPrefix);
            String essayHash = computeEssayHash(request.getEssay());
            int prefixChars = promptPrefix.length();
            int essayChars = safeText(request.getEssay()).length();
            ScoreRuntimeState runtimeState = loadCompatibleRuntimeState(promptContext, promptCacheKey);
            boolean essayHashChanged = runtimeState != null && !essayHash.equals(runtimeState.lastEssayHash());
            String cacheMode = resolvePromptCacheRetention(modelName);
            OpenAiResponsesTextResult openAiResult = scoreEssayWithRetry(
                    requestProvider,
                    requestId,
                    systemPrompt,
                    promptPrefix,
                    promptSuffix,
                    promptCacheKey,
                    null,
                    cacheMode,
                    promptContext.docId()
            );
            String raw = openAiResult.outputText();

            // Parse with 1 retry: if JSON parse fails, re-call OpenAI once
            EvaluationResult result;
            try {
                result = parseResult(raw, rubric, mode, request.getEssay());
            } catch (Exception parseEx) {
                log.warn("JSON parse failed, retrying once. requestId={} reason={}", requestId, parseEx.getMessage());
                openAiResult = scoreEssayWithRetry(
                        requestProvider,
                        requestId + "-retry",
                        systemPrompt,
                        promptPrefix,
                        promptSuffix,
                        promptCacheKey,
                        null,
                        cacheMode,
                        promptContext.docId()
                );
                raw = openAiResult.outputText();
                result = parseResult(raw, rubric, mode, request.getEssay());
            }

            // 评分页错误统计与右侧语法检查保持一致：默认复用 Lite Mode(grammar-check basic) 口径
            final String normalizedEssay = request.getEssay().replace("\r\n", "\n").replace("\r", "\n");
            List<WritingEvaluateResponse.ErrorDto> rawErrors = grammarCheckService.check(normalizedEssay, "lite");
            List<WritingEvaluateResponse.ErrorDto> displayErrors = trustedRewriteService.filterTrustedTrinkaSuggestions(
                    request.getUserId(),
                    request.getDocumentId(),
                    normalizedEssay,
                    rawErrors
            );

            log.info("[Evaluate] 错误统计 requestId={} source=grammarCheck(lite) rawCount={} displayCount={}",
                    requestId, rawErrors.size(), displayErrors.size());

            EvaluationResult enriched = new EvaluationResult(
                    result.mode(), result.gradeByDimension(), result.analysisByDimension(),
                    result.scoreByDimension(), result.priorityFocus(), result.priorityFocusDetail(),
                    displayErrors, result.aiSummary());

            WritingEvaluateResponse response = buildResponse(requestId, enriched, request, effectiveStage, mode, taskType, "ai", existingProfile);
            response.setRawErrorCount(countNonSuggestion(rawErrors));
            response.setDisplayErrorCount(response.getErrorCount());
            response.setInputTokens(openAiResult.inputTokens());
            response.setCachedTokens(openAiResult.cachedTokens());
            response.setPayloadBytes(openAiResult.payloadBytes());
            response.setPromptCacheKey(promptCacheKey);
            response.setCacheMode(cacheMode);
            BigDecimal cacheHitRate = computeCacheHitRate(openAiResult.inputTokens(), openAiResult.cachedTokens());
            response.setCacheHitRate(cacheHitRate);
            response.setInstructionsHash(instructionsHash);
            response.setCachedPrefixHash(cachedPrefixHash);
            response.setEssayHash(essayHash);
            saveRuntimeState(promptContext, promptCacheKey, openAiResult.responseId(), essayHash);
            log.info("score evaluation cache metrics requestId={} docId={} rubricKey={} promptCacheKey={} cacheMode={} inputTokens={} cachedTokens={} cacheHitRate={} payloadBytes={} prefixChars={} essayChars={} instructionsHash={} cachedPrefixHash={} essayHash={} essayHashChanged={} reusedPreviousResponseId={}",
                    requestId,
                    promptContext.docId(),
                    promptContext.rubricKey(),
                    promptCacheKey,
                    cacheMode,
                    openAiResult.inputTokens(),
                    openAiResult.cachedTokens(),
                    cacheHitRate,
                    openAiResult.payloadBytes(),
                    prefixChars,
                    essayChars,
                    instructionsHash,
                    cachedPrefixHash,
                    essayHash,
                    essayHashChanged,
                    false);
            if (persistSideEffects) {
                updateAbilityProfile(request.getUserId(), result.scoreByDimension(), rubric.getRubricKey(), requestProvider);
                saveEvaluationQuietly(request, mode, response, rubric, effectiveStage, requestProvider);
            }
            return response;
        } catch (Exception e) {
            log.warn("Evaluate with OpenAI failed. requestId={} mode={} reason={}", requestId, mode, e.getMessage());
            UserAbilityProfile existingProfile = persistSideEffects ? readProfileQuietly(request.getUserId()) : null;
            return buildRubricDefaultResponse(requestId, request, rubric, effectiveStage, mode, taskType, existingProfile);
        }
    }

    // ================================================================
    // Prompt construction
    // ================================================================

    private String buildSystemPrompt(String stage, String mode, String taskType) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        String addendum = scoreStagePromptPolicyRegistry.buildSystemPromptAddendum(stage, mode, taskType);
        if (!addendum.isBlank()) {
            sb.append(addendum);
        }
        return sb.toString();
    }

    private String buildUserPrompt(WritingEvaluateRequest request, String rubricText, String mode,
                                   String stage, String rubricKey, String taskType) {
        ScorePromptContext context = new ScorePromptContext(
                trimToNull(request.getDocumentId()),
                openAiClient.resolveModel(request.getAiProvider()),
                "score-v1",
                rubricKey == null ? "unknown" : rubricKey,
                stage,
                mode,
                taskType == null ? "unknown" : taskType,
                sha256(request.getTaskPrompt()),
                sha256(rubricText),
                trimToNull(request.getTaskPrompt()),
                trimToNull(request.getTopicTitle()),
                request.getMinWords(),
                request.getRecommendedMaxWords(),
                request.getMaxScore()
        );
        return buildPromptPrefix(rubricText, context) + buildDynamicPromptSuffix(request, context, mode, stage, taskType);
    }

    private String buildCachedPromptPrefix(ScorePromptContext context, String rubricText) {
        String cacheKey = buildPromptPrefixCacheKey(context);
        return promptPrefixCache.computeIfAbsent(cacheKey, key -> buildPromptPrefix(rubricText, context));
    }

    private String buildPromptPrefix(String rubricText, ScorePromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[SCORING_CONTEXT]\n");
        sb.append("study_stage=").append(context.studyStage()).append("\n");
        sb.append("mode=").append(context.mode()).append("\n");
        sb.append("rubric_key=").append(context.rubricKey()).append("\n");
        sb.append("task_type=").append(context.taskType()).append("\n\n");

        String stageRules = scoreStagePromptPolicyRegistry.buildStageRules(
                context.studyStage(),
                context.mode(),
                context.taskType()
        );
        if (!stageRules.isBlank()) {
            sb.append(stageRules).append("\n\n");
        }

        sb.append("[RUBRIC_FROM_DB]\n");
        sb.append(rubricText).append("\n\n");
        sb.append("以上评分标准为唯一依据，严格按此评分。\n\n");
        sb.append(CHINESE_LEARNER_ERRORS).append("\n");
        String taskRequirements = buildTaskRequirementsSection(context);
        if (!taskRequirements.isBlank()) {
            sb.append(taskRequirements).append("\n\n");
        }
        String documentContext = buildDocumentContextSection(context);
        if (!documentContext.isBlank()) {
            sb.append(documentContext).append("\n\n");
        }
        sb.append("[TASK]\n");
        sb.append(buildTaskSection(context.studyStage(), context.mode(), context.taskType(), hasTaskRequirements(context))).append("\n\n");
        sb.append("[OUTPUT_JSON_SCHEMA]\n");
        sb.append(buildOutputSchema(context.mode()));
        sb.append("\n");
        sb.append(buildFewShotExample(context.studyStage(), context.mode(), context.taskType()));
        return sb.toString();
    }

    private String buildDynamicPromptSuffix(WritingEvaluateRequest request,
                                            ScorePromptContext context,
                                            String mode,
                                            String stage,
                                            String taskType) {
        return """
                [ESSAY]
                \"\"\"
                %s
                \"\"\"
                
                """.formatted(safeText(request.getEssay()));
    }

    private String buildPromptPrefixCacheKey(ScorePromptContext context) {
        return String.join("|",
                normalizeCacheKeyPart(context.model()),
                normalizeCacheKeyPart(context.promptVersion()),
                normalizeCacheKeyPart(context.rubricKey()),
                normalizeCacheKeyPart(context.studyStage()),
                normalizeCacheKeyPart(context.mode()),
                normalizeCacheKeyPart(context.taskType())
        );
    }

    private OpenAiResponsesTextResult scoreEssayWithRetry(String requestProvider,
                                                          String traceId,
                                                          String systemPrompt,
                                                          String promptPrefix,
                                                          String promptSuffix,
                                                          String promptCacheKey,
                                                          String previousResponseId,
                                                          String cacheMode,
        String docId) {
        try {
            return callScoreModel(requestProvider, systemPrompt, promptPrefix, promptSuffix, promptCacheKey, previousResponseId, cacheMode);
        } catch (OpenAiResponsesException e) {
            if (previousResponseId != null && isInvalidPreviousResponseId(e)) {
                runtimeStateService.clear(docId);
                log.warn("score response invalid previous_response_id, retrying cold start traceId={} promptCacheKey={} reason={}",
                        traceId, promptCacheKey, e.getMessage());
                return callScoreModel(requestProvider, systemPrompt, promptPrefix, promptSuffix, promptCacheKey, null, cacheMode);
            }
            throw e;
        }
    }

    private OpenAiResponsesTextResult callScoreModel(String requestProvider,
                                                     String systemPrompt,
                                                     String promptPrefix,
                                                     String promptSuffix,
                                                     String promptCacheKey,
                                                     String previousResponseId,
                                                     String cacheMode) {
        return openAiClient.createTextResponse(new OpenAiResponsesTextRequest(
                requestProvider,
                openAiClient.resolveModel(requestProvider),
                systemPrompt,
                promptPrefix + promptSuffix,
                previousResponseId,
                promptCacheKey,
                cacheMode,
                true,
                8192
        ));
    }

    private ScoreRuntimeState loadCompatibleRuntimeState(ScorePromptContext context, String promptCacheKey) {
        String docId = trimToNull(context.docId());
        if (docId == null) {
            return null;
        }
        ScoreRuntimeState state = runtimeStateService.get(docId);
        if (state == null) {
            return null;
        }
        if (!isCompatible(state, context, promptCacheKey)) {
            runtimeStateService.clear(docId);
            return null;
        }
        return state;
    }

    private boolean isCompatible(ScoreRuntimeState state, ScorePromptContext context, String promptCacheKey) {
        return equalsNormalized(state.model(), context.model())
                && equalsNormalized(state.promptVersion(), context.promptVersion())
                && equalsNormalized(state.rubricKey(), context.rubricKey())
                && equalsNormalized(state.studyStage(), context.studyStage())
                && equalsNormalized(state.mode(), context.mode())
                && equalsNormalized(state.taskType(), context.taskType())
                && equalsNormalized(state.taskPromptHash(), context.taskPromptHash())
                && equalsNormalized(state.renderedRubricHash(), context.renderedRubricHash())
                && equalsNormalized(state.promptCacheKey(), promptCacheKey);
    }

    private void saveRuntimeState(ScorePromptContext context,
                                  String promptCacheKey,
                                  String responseId,
                                  String essayHash) {
        String docId = trimToNull(context.docId());
        if (docId == null || responseId == null || responseId.isBlank()) {
            return;
        }
        runtimeStateService.save(docId, new ScoreRuntimeState(
                context.model(),
                context.promptVersion(),
                context.rubricKey(),
                context.studyStage(),
                context.mode(),
                context.taskType(),
                context.taskPromptHash(),
                context.renderedRubricHash(),
                promptCacheKey,
                responseId,
                essayHash
        ));
    }

    private boolean isInvalidPreviousResponseId(OpenAiResponsesException e) {
        if (e == null) {
            return false;
        }
        if ("previous_response_id".equalsIgnoreCase(e.getErrorParam())) {
            return true;
        }
        String body = e.getResponseBody();
        return body != null && body.toLowerCase(Locale.ROOT).contains("previous_response_id");
    }

    private String resolvePromptCacheRetention(String model) {
        String normalizedModel = normalizeCacheKeyPart(model);
        if (normalizedModel.startsWith("gpt-4o")
                || normalizedModel.startsWith("gpt-4.1")
                || normalizedModel.startsWith("gpt-5")) {
            return "24h";
        }
        return "in_memory";
    }

    private String buildFewShotExample(String stage, String mode, String taskType) {
        return scoreStagePromptPolicyRegistry.resolveFewShotExample(stage, mode, taskType, DEFAULT_FEW_SHOT_EXAMPLE);
    }

    private String buildTaskSection(String stage, String mode, String taskType, boolean hasTaskPrompt) {
        StringBuilder sb = new StringBuilder();
        if ("exam".equals(mode)) {
            sb.append("评价维度（必须全部覆盖）：content_quality, task_achievement, structure, vocabulary, grammar, expression。\n");
        } else {
            sb.append("评价维度（必须全部覆盖）：content_quality, structure, vocabulary, grammar, expression。\n");
        }
        String taskAnchors = scoreStagePromptPolicyRegistry.buildTaskAnchors(stage, mode, taskType);
        if (!taskAnchors.isBlank()) {
            sb.append(taskAnchors);
        }
        sb.append("输出要求：\n");
        sb.append("1. 每个维度必须包含：\n");
        sb.append("   - strength：中文描述该维度最突出的亮点。\n");
        sb.append("   - strength_quote：从原文直接引用的亮点句或短语（与 strength 各司其职，不要重复内容）。\n");
        sb.append("   - weakness：聚焦该维度最关键的一个问题，用「原文引用 → 错误解释 → 正确写法」结构。\n");
        sb.append("   - weakness_quote：从原文直接引用的问题句或短语。\n");
        sb.append("   - suggestion：中文建议 + 英文改写示例。\n");
        if (hasTaskPrompt) {
            sb.append("   ★ task_achievement 必须对照题目要求逐项检查，并明确说明是否切题、是否完成任务、词数是否达标。\n");
        }
        sb.append("2. priority_focus：一个对象，包含 dimension（最需要改进的维度 key）、reason（为什么是这个维度）、action_item（一个具体的、今天就能做的行动建议）。\n");
        sb.append("3. summary：中文两段式评语：①肯定最大亮点（引原文）②指出 1-2 个核心问题。行动建议只写在 priority_focus.action_item 中，不要在 summary 中重复。若有偏题必须在 summary 中明确指出。\n");
        sb.append("4. 不要输出逐条 errors 清单；语法错误列表由外部语法检查链路提供。\n");
        sb.append("5. 只输出 JSON，不加任何解释或代码块。\n");
        return sb.toString();
    }

    private String buildTaskRequirementsSection(ScorePromptContext context) {
        if (!hasTaskRequirements(context)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[TASK_REQUIREMENTS]\n");
        sb.append("以下题目要求在 task_achievement 维度具有最高优先级，必须严格对照。\n");
        sb.append(context.taskPrompt()).append("\n");
        return sb.toString();
    }

    private boolean hasTaskRequirements(ScorePromptContext context) {
        return "exam".equals(context.mode())
                && context.taskPrompt() != null
                && !context.taskPrompt().isBlank();
    }

    private String buildDocumentContextSection(ScorePromptContext context) {
        StringBuilder sb = new StringBuilder();
        if (context.topicTitle() == null
                && context.minWords() == null
                && context.recommendedMaxWords() == null
                && context.maxScore() == null) {
            return "";
        }
        sb.append("[DOCUMENT_CONTEXT]\n");
        if (context.topicTitle() != null) {
            sb.append("topic_title=").append(context.topicTitle()).append("\n");
        }
        if (context.minWords() != null) {
            sb.append("min_words=").append(context.minWords()).append("\n");
        }
        if (context.recommendedMaxWords() != null) {
            sb.append("recommended_max_words=").append(context.recommendedMaxWords()).append("\n");
        }
        if (context.maxScore() != null) {
            sb.append("max_score=").append(context.maxScore()).append("\n");
        }
        return sb.toString().trim();
    }

    private String resolveModeForRequest(String stage, String requestedMode) {
        return rubricService.normalizeMode(requestedMode);
    }

    private WritingSessionMetadataResponse loadSessionMetadata(WritingEvaluateRequest request) {
        String docId = trimToNull(request.getDocumentId());
        Long userId = request.getUserId();
        if (docId == null || userId == null) {
            return null;
        }
        return documentService.getSessionMetadataByDocId(String.valueOf(userId), "default", docId, userId);
    }

    private boolean isPostgradExamContext(String stage, String mode) {
        return "postgrad".equals(stage) && "exam".equals(mode);
    }

    private String normalizeTaskType(String taskType) {
        if (taskType == null) {
            return null;
        }
        String normalized = taskType.trim().toLowerCase(Locale.ROOT);
        if ("task1".equals(normalized) || "task2".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private BigDecimal computeCacheHitRate(Integer inputTokens, Integer cachedTokens) {
        if (inputTokens == null || cachedTokens == null || inputTokens <= 0) {
            return null;
        }
        return BigDecimal.valueOf(cachedTokens)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(inputTokens), 1, RoundingMode.HALF_UP);
    }

    private String buildOutputSchema(String mode) {
        return """
                {
                  "mode": "%s",
                  "grades": { "<dimension_key>": "A|B|C|D|E" },
                  "analysis": {
                    "<dimension_key>": {
                      "strength": "<中文：优点描述，必须引用原文具体词句>",
                      "strength_quote": "<从原文直接引用的亮点句或短语>",
                      "weakness": "<中文：缺点描述，含原文引用→解释→正确写法>",
                      "weakness_quote": "<从原文直接引用的问题句或短语>",
                      "suggestion": "<中文建议 + 英文改写示例>"
                    }
                  },
                  "priority_focus": {
                    "dimension": "<最需要改进的维度 key>",
                    "reason": "<中文：为什么是这个维度>",
                    "action_item": "<中文：一个具体的、今天就能做的行动建议>"
                  },
                  "summary": "<中文两段式评语：①亮点（引原文）②核心问题>"
                }
                """.formatted(mode);
    }

    // ================================================================
    // Result parsing
    // ================================================================

    private EvaluationResult parseResult(String raw, RubricActiveResponse rubric,
                                          String mode, String essay) throws Exception {
        JsonNode root = parseJsonNode(raw);
        JsonNode gradesNode = root.path("grades");
        JsonNode analysisNode = root.path("analysis");
        JsonNode focusNode = root.path("priority_focus");
        String aiSummary = root.path("summary").asText("");

        Map<String, String> gradeByDimension = new LinkedHashMap<>();
        Map<String, WritingEvaluateResponse.AnalysisDto> analysisByDimension = new LinkedHashMap<>();
        Map<String, Integer> scoreByDimension = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> levelScoreMap = buildLevelScoreMap(rubric);

        for (RubricActiveResponse.DimensionDto d : rubric.getDimensions()) {
            String key = d.getDimensionKey();
            String grade = normalizeLevel(gradesNode.path(key).asText(DEFAULT_LEVEL));
            gradeByDimension.put(key, grade);

            Integer score = resolveScore(levelScoreMap, key, grade);
            scoreByDimension.put(key, score == null ? 60 : score);

            WritingEvaluateResponse.AnalysisDto analysis = parseAnalysisNode(analysisNode.path(key));
            fillDefaultAnalysis(analysis);
            analysisByDimension.put(key, analysis);
        }

        List<String> priorityFocus = new ArrayList<>();
        WritingEvaluateResponse.PriorityFocusDto priorityFocusDetail = null;
        if (focusNode.isObject()) {
            // New object format: { dimension, reason, action_item }
            String dim = focusNode.path("dimension").asText("").trim();
            if (scoreByDimension.containsKey(dim)) {
                priorityFocus.add(dim);
                priorityFocusDetail = new WritingEvaluateResponse.PriorityFocusDto(
                        dim,
                        focusNode.path("reason").asText(""),
                        focusNode.path("action_item").asText("")
                );
            }
        } else if (focusNode.isArray()) {
            // Backward-compatible array format
            for (JsonNode node : focusNode) {
                String key = node.asText("").trim();
                if (scoreByDimension.containsKey(key)) priorityFocus.add(key);
            }
        }
        if (priorityFocus.isEmpty()) {
            priorityFocus.addAll(findLowestDimensions(scoreByDimension, 2));
        }

        return new EvaluationResult(mode, gradeByDimension, analysisByDimension,
                scoreByDimension, priorityFocus, priorityFocusDetail, List.of(), aiSummary);
    }

    // ================================================================
    // Response building
    // ================================================================
    private WritingEvaluateResponse buildResponse(String requestId, EvaluationResult result,
                                                   WritingEvaluateRequest request,
                                                   String effectiveStage,
                                                   String mode,
                                                   String taskType,
                                                   String source,
                                                   UserAbilityProfile existingProfile) {
        int task = "exam".equals(mode)
                ? result.scoreByDimension().getOrDefault("task_achievement", 60)
                : result.scoreByDimension().getOrDefault("content_quality", 60);
        WritingExamPolicyService.ExamPolicyResult policyResult = writingExamPolicyService.evaluate(
                effectiveStage,
                mode,
                taskType,
                request,
                result.scoreByDimension(),
                result.errors()
        );
        int overall = policyResult.finalOverall();

        WritingEvaluateResponse.ScoreDto score = new WritingEvaluateResponse.ScoreDto();
        score.setTask(task);
        score.setCoherence(result.scoreByDimension().getOrDefault("structure", 60));
        score.setLexical(result.scoreByDimension().getOrDefault("vocabulary", 60));
        score.setGrammar(result.scoreByDimension().getOrDefault("grammar", 60));
        score.setOverall(overall);

        WritingEvaluateResponse.GaokaoScoreDto gaokaoScore = computeGaokaoScore(overall, mode);

        WritingEvaluateResponse response = new WritingEvaluateResponse();
        response.setRequestId(requestId);
        response.setMode(mode);
        response.setSource(source);
        response.setGrades(new LinkedHashMap<>(result.gradeByDimension()));
        response.setDimensionScores(new LinkedHashMap<>(result.scoreByDimension()));
        response.setAnalysis(new LinkedHashMap<>(result.analysisByDimension()));
        response.setPriorityFocus(new ArrayList<>(result.priorityFocus()));
        response.setPriorityFocusDetail(result.priorityFocusDetail());
        response.setScore(score);
        response.setGaokaoScore(gaokaoScore);
        response.setExamPolicy(toExamPolicyDto(policyResult));
        response.setImprovement(buildImprovement(existingProfile, gaokaoScore.getScore(), mode));
        response.setSummary(result.aiSummary().isBlank()
                ? "评分完成。重点提升方向：" + String.join("、", result.priorityFocus()) + "。"
                : result.aiSummary());
        response.setErrors(result.errors());
        response.setRawErrorCount(countNonSuggestion(result.errors()));
        response.setDisplayErrorCount(response.getErrorCount());
        log.info("[Evaluate] 响应携带 {} 条语法错误/建议", result.errors() != null ? result.errors().size() : 0);
        return response;
    }
    /** 与历史 EWA 均分对比，计算本次进退情况 */
    private WritingEvaluateResponse.ImprovementDto buildImprovement(
            UserAbilityProfile existing, int currentGaokao, String mode) {
        if (existing == null || existing.getAssessedScore() == null) return null;
        int maxScore = "exam".equals(mode) ? 25 : 15;
        int prevGaokao = (int) Math.round(existing.getAssessedScore().doubleValue() / 100.0 * maxScore);
        int delta = currentGaokao - prevGaokao;
        String message = WritingScoreUtils.buildImprovementMessage(delta);
        return new WritingEvaluateResponse.ImprovementDto(prevGaokao, currentGaokao, delta, message);
    }

    private UserAbilityProfile readProfileQuietly(Long userId) {
        if (userId == null) return null;
        try {
            return abilityProfileMapper.selectByUserId(userId);
        } catch (Exception e) {
            log.warn("readProfileQuietly failed. userId={} reason={}", userId, e.getMessage());
            return null;
        }
    }

    /** 换算成高考实际分制 */
    private WritingEvaluateResponse.GaokaoScoreDto computeGaokaoScore(int averageScore, String mode) {
        int maxScore = "exam".equals(mode) ? 25 : 15;
        int gaokaoScore = WritingScoreUtils.computeGaokaoRaw(averageScore, mode);
        String band = WritingScoreUtils.computeGaokaoband(gaokaoScore, mode);
        return new WritingEvaluateResponse.GaokaoScoreDto(gaokaoScore, maxScore, band);
    }

    private WritingEvaluateResponse.ExamPolicyDto toExamPolicyDto(WritingExamPolicyService.ExamPolicyResult policyResult) {
        if (policyResult == null || policyResult.policyKey() == null) {
            return null;
        }
        WritingEvaluateResponse.ExamPolicyDto dto = new WritingEvaluateResponse.ExamPolicyDto();
        dto.setPolicyKey(policyResult.policyKey());
        dto.setRawOverall(policyResult.rawOverall());
        dto.setFinalOverall(policyResult.finalOverall());
        dto.setCapScore(policyResult.capScore());
        dto.setDeductionTotal(policyResult.deductionTotal());
        dto.setFlags(policyResult.flags().isEmpty() ? null : new LinkedHashMap<>(policyResult.flags()));
        dto.setReasons(policyResult.reasons().isEmpty() ? null : new ArrayList<>(policyResult.reasons()));
        dto.setDirectionAssessment(toDirectionAssessmentDto(policyResult.directionAssessment()));
        return dto;
    }

    private WritingEvaluateResponse.DirectionAssessmentDto toDirectionAssessmentDto(
            WritingExamPolicyService.DirectionAssessment directionAssessment
    ) {
        if (directionAssessment == null) {
            return null;
        }
        WritingEvaluateResponse.DirectionAssessmentDto dto = new WritingEvaluateResponse.DirectionAssessmentDto(
                directionAssessment.relevance(),
                directionAssessment.taskCompletion(),
                directionAssessment.coverage(),
                directionAssessment.maxBand()
        );
        dto.setReasons(directionAssessment.reasons().isEmpty() ? null : new ArrayList<>(directionAssessment.reasons()));
        return dto;
    }
    // ================================================================
    // Ability profile update（EWA 指数加权平均）
    // ================================================================

    private void updateAbilityProfile(Long userId, Map<String, Integer> scoreByDimension, String rubricKey, String aiProvider) {
        if (userId == null) return;
        try {
            UserAbilityProfile existing = abilityProfileMapper.selectByUserId(userId);
            UserAbilityProfile updated = new UserAbilityProfile();
            updated.setUserId(userId);
            updated.setStage(1);

            int newCount = existing == null ? 1
                    : (existing.getSampleCount() == null ? 1 : existing.getSampleCount() + 1);
            updated.setSampleCount(newCount);

            updated.setGrammarScore(ewa(existing == null ? null : existing.getGrammarScore(),
                    scoreByDimension.get("grammar")));
            updated.setVocabularyScore(ewa(existing == null ? null : existing.getVocabularyScore(),
                    scoreByDimension.get("vocabulary")));
            updated.setStructureScore(ewa(existing == null ? null : existing.getStructureScore(),
                    scoreByDimension.get("structure")));
            updated.setCoherenceScore(ewa(existing == null ? null : existing.getCoherenceScore(),
                    scoreByDimension.get("structure")));
            Integer taskRaw = scoreByDimension.containsKey("task_achievement")
                    ? scoreByDimension.get("task_achievement")
                    : scoreByDimension.get("content_quality");
            updated.setTaskScore(ewa(existing == null ? null : existing.getTaskScore(), taskRaw));
            updated.setVarietyScore(ewa(existing == null ? null : existing.getVarietyScore(),
                    scoreByDimension.get("expression")));

            BigDecimal sum = updated.getGrammarScore()
                    .add(updated.getVocabularyScore())
                    .add(updated.getStructureScore())
                    .add(updated.getTaskScore())
                    .add(updated.getVarietyScore());
            updated.setAssessedScore(sum.divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP));
            updated.setConfidence(BigDecimal.valueOf(Math.min(1.0, newCount * 0.1))
                    .setScale(3, RoundingMode.HALF_UP));
            updated.setModelVersion(openAiClient.resolveModel(aiProvider));
            updated.setRubricVersion(trimToNull(rubricKey));
            updated.setUpdatedAt(LocalDateTime.now());

            abilityProfileMapper.upsertAbilityScores(updated);
            log.info("abilityProfile updated. userId={} sampleCount={} assessed={}",
                    userId, newCount, updated.getAssessedScore());
        } catch (Exception e) {
            log.warn("updateAbilityProfile failed. userId={} reason={}", userId, e.getMessage());
        }
    }

    private BigDecimal ewa(BigDecimal old, Integer newScore) {
        return WritingScoreUtils.ewa(old, newScore);
    }

    // ================================================================
    // Helpers
    // ================================================================

    private WritingEvaluateResponse.AnalysisDto parseAnalysisNode(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isObject()) {
            return new WritingEvaluateResponse.AnalysisDto();
        }
        WritingEvaluateResponse.AnalysisDto dto = new WritingEvaluateResponse.AnalysisDto(
                node.path("strength").asText(""),
                node.path("weakness").asText(""),
                node.path("suggestion").asText("")
        );
        dto.setQuote(node.path("quote").asText(""));
        dto.setStrengthQuote(node.path("strength_quote").asText(""));
        dto.setWeaknessQuote(node.path("weakness_quote").asText(""));
        return dto;
    }

    private void fillDefaultAnalysis(WritingEvaluateResponse.AnalysisDto a) {
        if (isBlank(a.getStrength()))   a.setStrength("该维度整体处于基础水平。");
        if (isBlank(a.getWeakness()))   a.setWeakness("该维度有明显提升空间，需加强针对性练习。");
        if (isBlank(a.getSuggestion())) a.setSuggestion("建议针对该维度做 1-2 句的专项修改后重新提交。");
    }

    private JsonNode parseJsonNode(String raw) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) trimmed = trimmed.substring(start, end + 1);
        } else if (!trimmed.startsWith("{")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) trimmed = trimmed.substring(start, end + 1);
        }
        return objectMapper.readTree(trimmed);
    }

    private Map<String, Map<String, Integer>> buildLevelScoreMap(RubricActiveResponse rubric) {
        Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
        for (RubricActiveResponse.DimensionDto d : rubric.getDimensions()) {
            Map<String, Integer> levelMap = new LinkedHashMap<>();
            for (RubricActiveResponse.LevelDto l : d.getLevels()) {
                levelMap.put(normalizeLevel(l.getLevel()), l.getScore());
            }
            m.put(d.getDimensionKey(), levelMap);
        }
        return m;
    }

    private Integer resolveScore(Map<String, Map<String, Integer>> scoreMap, String key, String level) {
        Map<String, Integer> levelMap = scoreMap.get(key);
        return levelMap == null ? null : levelMap.get(normalizeLevel(level));
    }

    private String normalizeLevel(String level) {
        return WritingScoreUtils.normalizeLevel(level);
    }

    private List<String> findLowestDimensions(Map<String, Integer> scores, int topN) {
        return scores.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int average(Map<String, Integer> m) {
        if (m == null || m.isEmpty()) return 60;
        int total = 0;
        for (Integer v : m.values()) total += v == null ? 60 : v;
        return Math.round(total / (float) m.size());
    }

    /** 评分后静默保存历史记录，失败不影响主流程 */
    private void saveEvaluationQuietly(WritingEvaluateRequest request, String mode,
                                       WritingEvaluateResponse response,
                                       RubricActiveResponse rubric,
                                       String effectiveStage,
                                       String aiProvider) {
        Long userId = request.getUserId();
        if (userId == null) return;
        try {
            EssayEvaluation record = writingEvaluationPersistenceService.persistSuccessfulEvaluation(
                    request,
                    mode,
                    response,
                    rubric,
                    effectiveStage,
                    openAiClient.resolveModel(aiProvider)
            );
            log.info("essayEvaluation saved. userId={} id={} docId={}",
                    userId,
                    record != null ? record.getId() : null,
                    record != null ? record.getDocumentId() : null);
        } catch (Exception e) {
            log.warn("saveEvaluation failed (non-fatal). userId={} reason={}", userId, e.getMessage());
        }
    }

    private String trimToNull(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    String computeEssayHash(String essay) {
        return sha256(safeText(essay));
    }

    String computePromptSegmentHash(String promptSegment) {
        return sha256(promptSegment == null ? "" : promptSegment);
    }

    private String sha256(String value) {
        String safe = value == null ? "" : value;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safe.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private boolean equalsNormalized(String left, String right) {
        return normalizeCacheKeyPart(left).equals(normalizeCacheKeyPart(right));
    }

    private String normalizeCacheKeyPart(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "unknown" : normalized.toLowerCase(Locale.ROOT);
    }

    private int countNonSuggestion(List<WritingEvaluateResponse.ErrorDto> errors) {
        if (errors == null || errors.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (WritingEvaluateResponse.ErrorDto error : errors) {
            if (error == null) continue;
            if (!"suggestion".equalsIgnoreCase(error.getCategory())) {
                total++;
            }
        }
        return total;
    }
    private boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }
    private String safeText(String text) { return text == null ? "" : text.trim(); }

    // ================================================================
    // Fallback responses
    // ================================================================

    private WritingEvaluateResponse buildRubricDefaultResponse(String requestId,
                                                                WritingEvaluateRequest request,
                                                                RubricActiveResponse rubric,
                                                                String effectiveStage,
                                                                String mode,
                                                                String taskType,
                                                                UserAbilityProfile existingProfile) {
        Map<String, String> grades = new LinkedHashMap<>();
        Map<String, WritingEvaluateResponse.AnalysisDto> analysis = new LinkedHashMap<>();
        Map<String, Integer> scores = new LinkedHashMap<>();

        for (RubricActiveResponse.DimensionDto d : rubric.getDimensions()) {
            grades.put(d.getDimensionKey(), DEFAULT_LEVEL);
            analysis.put(d.getDimensionKey(), new WritingEvaluateResponse.AnalysisDto(
                    "该维度整体处于基础水平。",
                    "该维度有明显提升空间，需加强针对性练习。",
                    "建议针对该维度做 1-2 句的专项修改后重新提交。"
            ));
            Integer defaultScore = d.getLevels().stream()
                    .filter(l -> "C".equalsIgnoreCase(l.getLevel()))
                    .map(RubricActiveResponse.LevelDto::getScore)
                    .findFirst().orElse(60);
            scores.put(d.getDimensionKey(), defaultScore);
        }

        List<String> focus = findLowestDimensions(scores, 2);
        EvaluationResult result = new EvaluationResult(mode, grades, analysis, scores,
                focus.isEmpty() ? List.of("grammar", "vocabulary") : focus,
                null, List.of(), "AI 评分暂时不可用，当前为默认评分。请稍后重试。");
        return buildResponse(requestId, result, request, effectiveStage, mode, taskType, "fallback", existingProfile);
    }

    private WritingEvaluateResponse buildLegacyFallback(String requestId) {
        WritingEvaluateResponse.ScoreDto score = new WritingEvaluateResponse.ScoreDto();
        score.setOverall(60);
        score.setTask(60);
        score.setCoherence(60);
        score.setLexical(60);
        score.setGrammar(60);

        WritingEvaluateResponse response = new WritingEvaluateResponse();
        response.setRequestId(requestId);
        response.setMode("free");
        response.setSource("fallback");
        response.setGrades(Map.of("content_quality","C","structure","C",
                "vocabulary","C","grammar","C","expression","C"));
        response.setDimensionScores(Map.of(
                "content_quality", 60,
                "structure", 60,
                "vocabulary", 60,
                "grammar", 60,
                "expression", 60
        ));
        response.setAnalysis(Map.of());
        response.setPriorityFocus(List.of("grammar", "vocabulary"));
        response.setScore(score);
        response.setGaokaoScore(computeGaokaoScore(60, "free"));
        response.setSummary("评分标准配置未找到，显示默认结果。请联系管理员检查 Rubric 配置。");
        response.setErrors(new ArrayList<>());
        return response;
    }

    private String buildFallbackRubricText(RubricActiveResponse rubric, String stage, String mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rubric (stage=").append(stage).append(", mode=").append(mode).append("):\n\n");
        for (RubricActiveResponse.DimensionDto d : rubric.getDimensions()) {
            sb.append(d.getDimensionKey()).append(" (").append(d.getDisplayName()).append("):\n");
            for (RubricActiveResponse.LevelDto l : d.getLevels()) {
                sb.append(l.getLevel()).append(": ").append(l.getCriteria()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    // ================================================================
    // Internal record
    // ================================================================

    private record EvaluationResult(
            String mode,
            Map<String, String> gradeByDimension,
            Map<String, WritingEvaluateResponse.AnalysisDto> analysisByDimension,
            Map<String, Integer> scoreByDimension,
            List<String> priorityFocus,
            WritingEvaluateResponse.PriorityFocusDto priorityFocusDetail,
            List<WritingEvaluateResponse.ErrorDto> errors,
            String aiSummary
    ) {}
}




