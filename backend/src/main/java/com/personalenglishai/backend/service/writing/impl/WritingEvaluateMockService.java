package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingEvaluateRequest;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WritingEvaluateMockService implements WritingEvaluateService {

    private static final Logger log = LoggerFactory.getLogger(WritingEvaluateMockService.class);
    private static final String DEFAULT_STAGE = "highschool";
    private static final String DEFAULT_LEVEL = "C";


    // ----------------------------------------------------------------
    // System prompt
    // ----------------------------------------------------------------
    private static final String SYSTEM_PROMPT = """
            你是一位有多年经验的英语写作阅卷老师。
            你的任务：严格按照当前学段、任务类型和评分标准逐维度评估，给出具体、有温度、可操作的反馈。

            强制规则：
            1. 严格按照提供的评分标准（[RUBRIC_FROM_DB]）打分。若本提示中的其他描述与评分标准有冲突，以评分标准为准。
            2. 所有维度必须全部评价，不得增减。
            3. 每个维度的 strength 用中文描述该维度最突出的亮点；strength_quote 单独提供从原文直接引用的亮点词句。两者各司其职，不要在 strength 中重复引用。
               如果学生用了超出其水平的表达，在 strength 中明确指出。即使是 D/E 级维度也必须找到至少一个正确点。
            4. 每个维度的 weakness 聚焦该维度最关键的一个问题，用「原文引用 → 错误解释 → 正确写法」结构描述；weakness_quote 单独提供对应的原文问题片段。
               逐条的详细错误放在 errors 数组中，weakness 不要罗列多条错误。
            5. errors 数组必须穷举全文所有语言错误和可改进之处，不设数量上限，宁多勿漏。每个错误独立一条，不要合并。original 必须从原文精确复制。
               errors 数组每条必须包含 category 字段：
               - "error"：客观语言错误（拼写、语法、时态、主谓不一致等），有明确正确答案
               - "suggestion"：非错误但可改进（用词基础、表达不地道、内容偏题、逻辑不连贯等）
            6. summary 用中文写成两段：①肯定最大亮点（引用原文）②指出 1-2 个核心问题。语气像一位友善的英语老师在作文本上手写评语。
               行动建议只写在 priority_focus.action_item 中，summary 里不要重复。
            7. 只输出合法 JSON。

            量化锚点（结合 [文本统计] 计算，有犹豫时偏严）：
            - grammar: 错误密度=语法错误数÷总词数×100。A≤1, B=2-3, C=4-5, D=6-8, E>8
            - vocabulary: 高级词占比=高级词数÷总词数×100%。A>20%, B=10-20%, C=5-10%, D<5%, E=基础词也频繁用错
            - structure: A=段落清晰+过渡自然+首尾呼应, B=段落清晰+有过渡, C=有分段但过渡生硬, D=分段混乱, E=无段落意识
            - content_quality: A=观点鲜明+论据充分+有深度, B=观点清晰+有论据, C=有观点但论据薄弱, D=观点模糊, E=无明确观点
            - expression: A=句式多样+地道表达, B=有一定句式变化, C=句式单一但基本正确, D=大量简单句+中式英语, E=表达严重不通顺
            - 如果某维度在两个等级之间犹豫，选择较低等级。

            反馈语言规范：
            - 禁止使用"总体来说""有待提高""建议加强""整体水平中等"等空泛措辞。
            - 反馈应基于原文中的具体证据。对于"缺少某元素"的问题（如缺少过渡句、缺少结尾段），直接说明缺少什么以及为什么重要，不需要强行引用原文。

            关于 errors 数组的重要提醒：
            - 必须逐句扫描全文，穷举每一处语法错误、拼写错误、搭配错误和可改进表达。
            - 不要因为错误数量多就省略，学生需要看到所有问题才能有效改进。
            - 同一个句子中有多个独立错误时，每个错误单独一条。
            - 示例中的 errors 数量仅为格式参考，实际应根据全文错误数量如实列出。
            """;

    // ----------------------------------------------------------------
    // Chinese-learner high-frequency error patterns
    // ----------------------------------------------------------------
    private static final String CHINESE_LEARNER_ERRORS = """
            [中国学生常见英文写作错误模式]
            批改时必须逐句检查以下所有错误类型，发现即收录，不得遗漏（必须从原文精确引用错误片段）：
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

    private static final String POSTGRAD_TASK1_FEW_SHOT_EXAMPLE = """
            [评分示例 — postgrad exam task1 功能写作]
            题目类型：投诉/建议类书信。
            高分作文必须同时满足：写作目的明确、对象意识正确、语气得体、格式基本规范、关键信息完整。
            若缺少称呼、请求动作或结尾，task_achievement 不得给高档。
            示例输出片段：
            {"mode":"exam","grades":{"task_achievement":"B","content_quality":"B","structure":"B","vocabulary":"C","grammar":"C","expression":"B"}}
            """;

    private static final String POSTGRAD_TASK2_FEW_SHOT_EXAMPLE = """
            [评分示例 — postgrad exam task2 材料作文]
            题目类型：图画/图表/现象评论类大作文。
            高分作文必须同时完成：描述材料、解读含义、给出评论。
            如果只有空泛评论而没有描述材料，task_achievement 不得给高档。
            示例输出片段：
            {"mode":"exam","grades":{"task_achievement":"B","content_quality":"B","structure":"B","vocabulary":"B","grammar":"C","expression":"B"}}
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

    public WritingEvaluateMockService(
            RubricService rubricService,
            RubricTextBuilder rubricTextBuilder,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper,
            UserAbilityProfileMapper abilityProfileMapper,
            WritingEvaluationPersistenceService writingEvaluationPersistenceService,
            WritingExamPolicyService writingExamPolicyService,
            GrammarCheckService grammarCheckService,
            TrustedRewriteService trustedRewriteService
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
        String requestedStage = rubricService.normalizeStage(request.getStudyStage());
        String mode = resolveModeForRequest(requestedStage, request.getMode());
        String taskType = normalizeTaskType(request.getTaskType());
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
            String systemPrompt = buildSystemPrompt(effectiveStage, mode, taskType);
            String userPrompt = buildUserPrompt(request, rubricText, mode, effectiveStage, rubric.getRubricKey(), taskType);
            String raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, requestId, 0.4, 8192);

            // Parse with 1 retry: if JSON parse fails, re-call OpenAI once
            EvaluationResult result;
            try {
                result = parseResult(raw, rubric, mode, request.getEssay());
            } catch (Exception parseEx) {
                log.warn("JSON parse failed, retrying once. requestId={} reason={}", requestId, parseEx.getMessage());
                raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, requestId + "-retry", 0.4, 8192);
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
            if (persistSideEffects) {
                updateAbilityProfile(request.getUserId(), result.scoreByDimension(), rubric.getRubricKey());
                saveEvaluationQuietly(request, mode, response, rubric, effectiveStage);
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
        if (isPostgradExamContext(stage, mode)) {
            sb.append("\n\n[考研评分场景]\n");
            sb.append("当前学段为 postgrad exam。必须坚持任务与内容优先，先判断是否切题、是否完成任务，再判断语言质量。\n");
            if ("task1".equals(taskType)) {
                sb.append("task1 是功能写作。必须检查写作目的、对象意识、语气、格式和要点覆盖，不得按泛议论文标准打分。\n");
            } else if ("task2".equals(taskType)) {
                sb.append("task2 是材料作文。必须检查描述材料、解读含义、给出评论三个动作，不得因为语言漂亮就忽略材料描述缺失。\n");
            } else {
                sb.append("taskType 未明确时，只能保守推断任务类型；若拿不准，评分应偏严，不得给高置信高分。\n");
            }
        }
        return sb.toString();
    }

    private String buildUserPrompt(WritingEvaluateRequest request, String rubricText, String mode,
                                   String stage, String rubricKey, String taskType) {
        StringBuilder sb = new StringBuilder();
        sb.append("[SCORING_CONTEXT]\n");
        sb.append("study_stage=").append(stage).append("\n");
        sb.append("mode=").append(mode).append("\n");
        sb.append("rubric_key=").append(rubricKey == null ? "unknown" : rubricKey).append("\n");
        sb.append("task_type=").append(taskType == null ? "unknown" : taskType).append("\n\n");

        if (isPostgradExamContext(stage, mode)) {
            sb.append(buildPostgradStageRules(taskType)).append("\n\n");
        }

        boolean hasTaskPrompt = "exam".equals(mode)
                && request.getTaskPrompt() != null
                && !request.getTaskPrompt().isBlank();

        if (hasTaskPrompt) {
            sb.append("═══════════════════════════════════════\n");
            sb.append("[考试题目要求] — 以下是本次考试的写作题目，task_achievement 维度必须严格对照此题评分：\n");
            sb.append(request.getTaskPrompt().trim()).append("\n");
            sb.append("═══════════════════════════════════════\n\n");
            sb.append("⚠️ task_achievement 评分强制规则（必须严格执行，不得偏宽）：\n");
            sb.append("  analysis.task_achievement.weakness 必须明确回答：作文是否切题、任务是否完成、字数是否达标。\n");
            sb.append("  如题目要求与作文主题明显无关，必须在 task_achievement 与 summary 中明确指出。\n");
            sb.append("  注意：若与 [RUBRIC_FROM_DB] 冲突，以 [RUBRIC_FROM_DB] 为准。\n\n");
        }

        sb.append("[RUBRIC_FROM_DB]\n");
        sb.append(rubricText).append("\n\n");
        sb.append("以上评分标准为唯一依据，严格按此评分。\n\n");
        sb.append(CHINESE_LEARNER_ERRORS).append("\n");
        sb.append("[TASK]\n");
        sb.append(buildTaskSection(stage, mode, taskType, hasTaskPrompt)).append("\n\n");

        if (request.getAiHint() != null && !request.getAiHint().isBlank()) {
            sb.append("教师补充提示：").append(request.getAiHint().trim()).append("\n\n");
        }

        String essay = safeText(request.getEssay());
        int wordCount = countWords(essay);
        int sentenceCount = countSentences(essay);
        int paragraphCount = countParagraphs(essay);
        sb.append("[文本统计]\n");
        sb.append("总词数：").append(wordCount).append(" 词\n");
        sb.append("句子数：").append(sentenceCount).append(" 句\n");
        sb.append("段落数：").append(paragraphCount).append(" 段\n");
        sb.append("请结合以上统计数据应用量化锚点判分（如 grammar 错误密度 = 错误数 / 总词数 × 100）。\n\n");

        sb.append("学生作文：\n\"\"\"\n")
                .append(essay)
                .append("\n\"\"\"\n\n");

        sb.append("[OUTPUT_JSON_SCHEMA]\n");
        sb.append(buildOutputSchema(mode));
        sb.append("\n");
        sb.append(buildFewShotExample(stage, mode, taskType));
        return sb.toString();
    }

    private String buildPostgradStageRules(String taskType) {
        StringBuilder sb = new StringBuilder();
        sb.append("[POSTGRAD_STAGE_RULES]\n");
        sb.append("这是 postgrad exam 写作评分场景，统一按 100 分制和 Band 1-5 的考研语义理解评分结果。\n");
        sb.append("必须先判任务方向和任务完成度，再判内容质量和语言质量。\n");
        sb.append("语言质量不能覆盖严重偏题、材料未解读或任务未完成。\n");
        if ("task1".equals(taskType)) {
            sb.append("当前任务类型为 task1：功能写作。重点检查写作目的、收信对象和身份关系、语气、格式、要点覆盖。\n");
        } else if ("task2".equals(taskType)) {
            sb.append("当前任务类型为 task2：材料作文。重点检查描述材料、解读含义、给出评论三个动作是否完整。\n");
        } else {
            sb.append("当前 taskType 未明确。若无法从题目可靠判断，只能保守评分，不得高估 task_achievement。\n");
        }
        return sb.toString().trim();
    }

    private String buildFewShotExample(String stage, String mode, String taskType) {
        if (!isPostgradExamContext(stage, mode)) {
            return DEFAULT_FEW_SHOT_EXAMPLE;
        }
        if ("task1".equals(taskType)) {
            return POSTGRAD_TASK1_FEW_SHOT_EXAMPLE;
        }
        if ("task2".equals(taskType)) {
            return POSTGRAD_TASK2_FEW_SHOT_EXAMPLE;
        }
        return POSTGRAD_TASK1_FEW_SHOT_EXAMPLE + "\n\n" + POSTGRAD_TASK2_FEW_SHOT_EXAMPLE;
    }

    private String buildTaskSection(String stage, String mode, String taskType, boolean hasTaskPrompt) {
        StringBuilder sb = new StringBuilder();
        if ("exam".equals(mode)) {
            sb.append("评价维度（必须全部覆盖）：content_quality, task_achievement, structure, vocabulary, grammar, expression。\n");
        } else {
            sb.append("评价维度（必须全部覆盖）：content_quality, structure, vocabulary, grammar, expression。\n");
        }
        if (isPostgradExamContext(stage, mode)) {
            if ("task1".equals(taskType)) {
                sb.append("考研 task1 判分锚点：功能写作；必须检查写作目的、收信对象和身份关系、语气、格式、要点覆盖；不要按泛议论文标准打分。\n");
            } else if ("task2".equals(taskType)) {
                sb.append("考研 task2 判分锚点：必须完成“描述材料 + 解读含义 + 给出评论”；不要只给空泛评论而忽略材料描述。\n");
            } else {
                sb.append("考研主路径建议显式提供 taskType。若缺失，只能保守推断 task1/task2，不得高估任务完成度。\n");
            }
        }
        sb.append("输出要求：\n");
        sb.append("1. 每个维度必须包含：\n");
        sb.append("   - strength：中文描述该维度最突出的亮点。\n");
        sb.append("   - strength_quote：从原文直接引用的亮点句或短语（与 strength 各司其职，不要重复内容）。\n");
        sb.append("   - weakness：聚焦该维度最关键的一个问题，用「原文引用 → 错误解释 → 正确写法」结构。逐条错误放在 errors 数组中。\n");
        sb.append("   - weakness_quote：从原文直接引用的问题句或短语。\n");
        sb.append("   - suggestion：中文建议 + 英文改写示例。\n");
        if (hasTaskPrompt) {
            sb.append("   ★ task_achievement 必须对照题目要求逐项检查，并明确说明是否切题、是否完成任务、词数是否达标。\n");
        }
        sb.append("2. errors[]：穷举全文所有语言错误和可改进之处，不设数量上限，宁多勿漏。original 必须从原文精确复制，reason 用中文说明。\n");
        sb.append("3. priority_focus：一个对象，包含 dimension（最需要改进的维度 key）、reason（为什么是这个维度）、action_item（一个具体的、今天就能做的行动建议）。\n");
        sb.append("4. summary：中文两段式评语：①肯定最大亮点（引原文）②指出 1-2 个核心问题。行动建议只写在 priority_focus.action_item 中，不要在 summary 中重复。若有偏题必须在 summary 中明确指出。\n");
        sb.append("5. 只输出 JSON，不加任何解释或代码块。\n");
        return sb.toString();
    }

    private String resolveModeForRequest(String stage, String requestedMode) {
        return rubricService.normalizeMode(requestedMode);
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

    private int countWords(String text) {
        return WritingScoreUtils.countWords(text);
    }

    private int countSentences(String text) {
        return WritingScoreUtils.countSentences(text);
    }

    private int countParagraphs(String text) {
        return WritingScoreUtils.countParagraphs(text);
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
                  "errors": [
                    {
                      "original": "<从原文精确复制的错误短语>",
                      "suggestion": "<修改后的正确写法>",
                      "type": "spelling|morphology|subject_verb|tense|article|preposition|collocation|syntax|word_choice|part_of_speech|punctuation|logic",
                      "category": "error|suggestion",
                      "severity": "major|minor",
                      "reason": "<中文：说明错误原因，若为中式英语请标注>"
                    }
                  ],
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
    // Dedicated error detection (second AI call)
    // ================================================================

    private static final String ERROR_DETECTION_SYSTEM = """
            你是一位专业的英语语法检查工具。你的唯一任务是逐句检查学生作文中的每一处语言问题。

            检查范围（必须全部覆盖）：
            - 拼写错误
            - 动词形态（时态、单复数、不规则变化）
            - 主谓一致
            - 冠词使用（a/an/the 缺失或多余）
            - 介词搭配
            - 词性错误
            - 句法结构（残缺句、并列结构不平行等）
            - 用词不当或不地道（中式英语）
            - 标点符号
            - 名词单复数
            - 代词指代不明
            - 连词和过渡词使用

            强制要求：
            1. 逐句扫描，每句话至少检查以上所有类别
            2. 每个独立错误单独一条，不要合并
            3. original 必须从原文精确复制，一字不差
            4. 即使是轻微问题也要列出
            5. 只输出 JSON 数组，不加任何其他内容

            输出格式：
            [
              {"original":"<原文片段>","suggestion":"<修正>","type":"<类型>","category":"error|suggestion","severity":"major|minor","reason":"<中文原因>"}
            ]
            """;

    private List<WritingEvaluateResponse.ErrorDto> runDedicatedErrorDetection(String essay, String traceId) {
        try {
            String userPrompt = "请逐句检查以下作文的所有语言错误：\n\n" + essay;
            String raw = openAiClient.callWithTraceId(ERROR_DETECTION_SYSTEM, userPrompt, traceId, 0.6, 8192);
            return parseDedicatedErrors(raw, essay);
        } catch (Exception e) {
            log.warn("Dedicated error detection failed. traceId={} reason={}", traceId, e.getMessage());
            return List.of();
        }
    }

    private List<WritingEvaluateResponse.ErrorDto> parseDedicatedErrors(String raw, String essay) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            String cleaned = raw.trim();
            // Strip markdown code fences
            if (cleaned.startsWith("```")) {
                int first = cleaned.indexOf('\n');
                int last = cleaned.lastIndexOf("```");
                if (first > 0 && last > first) cleaned = cleaned.substring(first + 1, last).trim();
            }
            JsonNode arr = objectMapper.readTree(cleaned);
            if (!arr.isArray()) {
                // Maybe wrapped in an object
                if (arr.has("errors") && arr.get("errors").isArray()) {
                    arr = arr.get("errors");
                } else {
                    return List.of();
                }
            }
            return parseAiErrors(arr, essay);
        } catch (Exception e) {
            log.warn("Failed to parse dedicated error response: {}", e.getMessage());
            return List.of();
        }
    }

    /** Merge errors from two sources, dedup by original text overlap */
    private List<WritingEvaluateResponse.ErrorDto> mergeErrors(
            List<WritingEvaluateResponse.ErrorDto> primary,
            List<WritingEvaluateResponse.ErrorDto> secondary,
            String essay) {
        List<WritingEvaluateResponse.ErrorDto> merged = new ArrayList<>(primary);
        // Track existing originals (lowercase) for dedup
        var existingOriginals = new java.util.HashSet<String>();
        for (var e : primary) {
            if (e.getOriginal() != null) {
                existingOriginals.add(e.getOriginal().toLowerCase(Locale.ROOT).trim());
            }
        }
        int nextId = primary.size() + 1;
        for (var e : secondary) {
            String orig = e.getOriginal();
            if (orig == null || orig.isBlank()) continue;
            String key = orig.toLowerCase(Locale.ROOT).trim();
            if (existingOriginals.contains(key)) continue;
            // Also check if this original is a substring of an existing one or vice versa
            boolean overlap = false;
            for (String existing : existingOriginals) {
                if (key.contains(existing) || existing.contains(key)) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;
            e.setId("e" + nextId++);
            // 若 secondary 已有有效 span（如来自 LanguageTool），直接保留，避免重复匹配或错位
            if (e.getSpan() != null && e.getSpan().getStart() < e.getSpan().getEnd()) {
                // keep e.getOriginal() and e.getSpan() as-is
            } else {
                MatchResult m = matchAndCorrect(essay, orig);
                e.setSpan(m.span());
                e.setOriginal(m.correctedOriginal());
            }
            merged.add(e);
            existingOriginals.add(key);
        }
        // Sort by span start position
        merged.sort(Comparator.comparingInt(e -> e.getSpan() != null ? e.getSpan().getStart() : 0));
        // Re-assign sequential IDs
        int id = 1;
        for (var e : merged) e.setId("e" + id++);
        return merged;
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

        List<WritingEvaluateResponse.ErrorDto> errors = parseAiErrors(root.path("errors"), essay);

        return new EvaluationResult(mode, gradeByDimension, analysisByDimension,
                scoreByDimension, priorityFocus, priorityFocusDetail, errors, aiSummary);
    }

    /** 解析 AI errors 数组，字符串匹配计算真实 span，校正 original 为作文实际文本 */
    private List<WritingEvaluateResponse.ErrorDto> parseAiErrors(JsonNode errorsNode, String essay) {
        List<WritingEvaluateResponse.ErrorDto> result = new ArrayList<>();
        if (errorsNode == null || !errorsNode.isArray()) return result;

        int idx = 1;
        for (JsonNode node : errorsNode) {
            String original = node.path("original").asText("").trim();
            String suggestion = node.path("suggestion").asText("").trim();
            String type = node.path("type").asText("grammar").trim();
            String category = node.path("category").asText("").trim();
            String severity = node.path("severity").asText("minor").trim();
            String reason = node.path("reason").asText("").trim();

            if (original.isBlank() && suggestion.isBlank()) continue;

            // 匹配 span 并校正 original 为作文中的实际文本
            MatchResult match = matchAndCorrect(essay, original);

            WritingEvaluateResponse.ErrorDto dto = new WritingEvaluateResponse.ErrorDto();
            dto.setId("e" + idx++);
            dto.setType(normalizeErrorType(type));
            dto.setCategory("suggestion".equals(category) ? "suggestion" : "error");
            dto.setSeverity("major".equals(severity) ? "major" : "minor");
            dto.setSuggestion(suggestion.isBlank() ? reason : suggestion);
            dto.setOriginal(match.correctedOriginal);
            dto.setReason(reason.isBlank() ? null : reason);
            dto.setSpan(match.span);
            result.add(dto);
        }
        return result;
    }

    private record MatchResult(WritingEvaluateResponse.SpanDto span, String correctedOriginal) {}

    /**
     * 在原文中定位 original 片段，返回 span 和校正后的 original（从作文中精确截取）。
     * 匹配策略：精确 → 忽略大小写 → 规范化空白 → 子串模糊匹配
     */
    private MatchResult matchAndCorrect(String essay, String original) {
        WritingEvaluateResponse.SpanDto noMatch = new WritingEvaluateResponse.SpanDto(0, 0);
        if (original == null || original.isBlank() || essay == null) {
            return new MatchResult(noMatch, original);
        }

        // 1. 精确匹配
        int pos = essay.indexOf(original);
        if (pos >= 0) {
            return new MatchResult(
                    new WritingEvaluateResponse.SpanDto(pos, pos + original.length()),
                    essay.substring(pos, pos + original.length()));
        }

        // 2. 忽略大小写
        String lowerEssay = essay.toLowerCase(Locale.ROOT);
        String lowerOrig = original.toLowerCase(Locale.ROOT).trim();
        pos = lowerEssay.indexOf(lowerOrig);
        if (pos >= 0) {
            String actual = essay.substring(pos, pos + lowerOrig.length());
            return new MatchResult(
                    new WritingEvaluateResponse.SpanDto(pos, pos + lowerOrig.length()),
                    actual);
        }

        // 3. 规范化空白后匹配（作文中换行/多空格压缩为单空格）
        String normEssay = essay.replaceAll("\\s+", " ");
        String normOrig = original.replaceAll("\\s+", " ").trim();
        pos = normEssay.toLowerCase(Locale.ROOT).indexOf(normOrig.toLowerCase(Locale.ROOT));
        if (pos >= 0) {
            // 映射规范化位置回原文位置
            int[] mapping = buildNormToOrigMapping(essay);
            int origStart = mapping[pos];
            int origEnd = mapping[Math.min(pos + normOrig.length(), mapping.length - 1)];
            String actual = essay.substring(origStart, origEnd);
            return new MatchResult(
                    new WritingEvaluateResponse.SpanDto(origStart, origEnd),
                    actual);
        }

        // 4. 取 original 中间最长的连续词串（≥4词）尝试子串匹配
        String[] words = normOrig.split("\\s+");
        if (words.length >= 4) {
            // 尝试用中间 70% 的词做匹配，避免首尾差异
            int from = words.length / 6;
            int to = words.length - words.length / 6;
            String core = String.join(" ", java.util.Arrays.copyOfRange(words, from, to));
            int corePos = normEssay.toLowerCase(Locale.ROOT).indexOf(core.toLowerCase(Locale.ROOT));
            if (corePos >= 0) {
                // 向两边扩展到句子/子句边界
                int expandStart = corePos;
                int expandEnd = corePos + core.length();
                // 向左扩展到上一个句号/分号/段落 或 original 长度
                int targetLen = normOrig.length();
                int leftExtra = (targetLen - core.length()) / 2;
                expandStart = Math.max(0, expandStart - leftExtra);
                expandEnd = Math.min(normEssay.length(), expandStart + targetLen);
                expandStart = Math.max(0, expandEnd - targetLen);

                int[] mapping = buildNormToOrigMapping(essay);
                int origStart = mapping[Math.min(expandStart, mapping.length - 1)];
                int origEnd = mapping[Math.min(expandEnd, mapping.length - 1)];
                String actual = essay.substring(origStart, origEnd);
                return new MatchResult(
                        new WritingEvaluateResponse.SpanDto(origStart, origEnd),
                        actual);
            }
        }

        return new MatchResult(noMatch, original);
    }

    /** 构建规范化文本位置 → 原始文本位置的映射数组 */
    private int[] buildNormToOrigMapping(String text) {
        // normText = text.replaceAll("\\s+", " ")
        // mapping[normPos] = origPos
        List<Integer> map = new ArrayList<>(text.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            boolean isSpace = Character.isWhitespace(text.charAt(i));
            if (isSpace && lastWasSpace) continue;
            map.add(i);
            lastWasSpace = isSpace;
        }
        map.add(text.length()); // sentinel
        return map.stream().mapToInt(Integer::intValue).toArray();
    }

    private String normalizeErrorType(String type) {
        if (type == null) return "syntax";
        return switch (type.toLowerCase(Locale.ROOT)) {
            // 12 new fine-grained types — pass through
            case "spelling" -> "spelling";
            case "morphology" -> "morphology";
            case "subject_verb" -> "subject_verb";
            case "tense" -> "tense";
            case "article" -> "article";
            case "preposition" -> "preposition";
            case "collocation" -> "collocation";
            case "syntax" -> "syntax";
            case "word_choice" -> "word_choice";
            case "part_of_speech" -> "part_of_speech";
            case "punctuation" -> "punctuation";
            case "logic" -> "logic";
            // backward compat: map old types to new
            case "grammar" -> "syntax";
            case "vocabulary" -> "word_choice";
            case "expression" -> "collocation";
            case "coherence", "structure" -> "logic";
            case "format", "task" -> "punctuation";
            default -> "syntax";
        };
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
        log.info("[Evaluate] GPT 检查到 {} 个错误/建议", result.errors() != null ? result.errors().size() : 0);
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

    private void updateAbilityProfile(Long userId, Map<String, Integer> scoreByDimension, String rubricKey) {
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
            updated.setModelVersion(openAiClient.getModel());
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
                                       String effectiveStage) {
        Long userId = request.getUserId();
        if (userId == null) return;
        try {
            EssayEvaluation record = writingEvaluationPersistenceService.persistSuccessfulEvaluation(
                    request,
                    mode,
                    response,
                    rubric,
                    effectiveStage,
                    openAiClient.getModel()
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




