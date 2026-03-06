package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.prompt.AbilityPromptBuilder;
import com.personalenglishai.backend.dto.writing.PolishEssayRequest;
import com.personalenglishai.backend.dto.writing.PolishEssayResponse;
import com.personalenglishai.backend.dto.writing.PolishRequest;
import com.personalenglishai.backend.dto.writing.PolishResponse;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WritingPolishServiceImpl implements WritingPolishService {

    private static final Logger log = LoggerFactory.getLogger(WritingPolishServiceImpl.class);

    private static final Map<String, String> TIER_INSTRUCTIONS = Map.of(
            "basic", "保持在学生当前水平，仅修正最明显的不自然之处，不引入超出当前能力的词汇。",
            "steady", "比学生当前水平提升一小步，可引入 1 个学生易学的新表达。",
            "advanced", "明显提升表达水平，使用更丰富的句式和精准词汇。",
            "perfect", "不受学生水平限制，使用最地道的母语者表达。"
    );

    private final OpenAiClient openAiClient;
    private final AbilityPromptBuilder abilityPromptBuilder;
    private final UserAbilityProfileMapper userAbilityProfileMapper;
    private final ObjectMapper objectMapper;

    public WritingPolishServiceImpl(OpenAiClient openAiClient,
                                    AbilityPromptBuilder abilityPromptBuilder,
                                    UserAbilityProfileMapper userAbilityProfileMapper,
                                    ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.abilityPromptBuilder = abilityPromptBuilder;
        this.userAbilityProfileMapper = userAbilityProfileMapper;
        this.objectMapper = objectMapper;
    }

    // ════════════════════════════════════════════════════════════════
    //  单句润色（保留向后兼容）
    // ════════════════════════════════════════════════════════════════

    @Override
    public PolishResponse polish(PolishRequest request) {
        Long userId = request.getUserId();
        String tier = normalizeTier(request.getTier());
        String traceId = "polish-" + UUID.randomUUID().toString().substring(0, 8);

        UserAbilityProfile profile = userId != null
                ? userAbilityProfileMapper.selectByUserId(userId)
                : null;

        String abilityPrompt = abilityPromptBuilder.buildAbilityPrompt(profile, userId);
        String tierInstruction = TIER_INSTRUCTIONS.getOrDefault(tier, TIER_INSTRUCTIONS.get("steady"));
        String systemPrompt = buildSingleSystemPrompt(abilityPrompt, tierInstruction);
        String userPrompt = buildSingleUserPrompt(request);

        log.info("[POLISH] traceId={} userId={} tier={} originalLen={}",
                traceId, userId, tier, request.getOriginal().length());

        String rawResponse = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId);
        return parseSingleResponse(rawResponse, traceId);
    }

    // ════════════════════════════════════════════════════════════════
    //  全文逐句润色（一次 GPT 调用）
    // ════════════════════════════════════════════════════════════════

    @Override
    public PolishEssayResponse polishEssay(PolishEssayRequest request) {
        Long userId = request.getUserId();
        String tier = normalizeTier(request.getTier());
        String traceId = "polish-essay-" + UUID.randomUUID().toString().substring(0, 8);

        UserAbilityProfile profile = userId != null
                ? userAbilityProfileMapper.selectByUserId(userId)
                : null;

        String abilityPrompt = abilityPromptBuilder.buildAbilityPrompt(profile, userId);
        String tierInstruction = TIER_INSTRUCTIONS.getOrDefault(tier, TIER_INSTRUCTIONS.get("steady"));

        String systemPrompt = buildEssaySystemPrompt(abilityPrompt, tierInstruction);
        String userPrompt = "Essay:\n\n" + request.getText().trim();

        log.info("[POLISH-ESSAY] traceId={} userId={} tier={} textLen={}",
                traceId, userId, tier, request.getText().length());

        long start = System.currentTimeMillis();
        String rawResponse = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId,
                0.7, 4096);
        long elapsed = System.currentTimeMillis() - start;

        PolishEssayResponse response = parseEssayResponse(rawResponse, traceId);
        log.info("[POLISH-ESSAY] traceId={} sentences={} elapsed={}ms",
                traceId, response.getSentences() != null ? response.getSentences().size() : 0, elapsed);
        return response;
    }

    // ── helpers ──

    private String normalizeTier(String tier) {
        if (tier == null) return "steady";
        String t = tier.trim().toLowerCase();
        return TIER_INSTRUCTIONS.containsKey(t) ? t : "steady";
    }

    private String stripCodeFences(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    // ── 单句润色 prompt / parse ──

    private String buildSingleSystemPrompt(String abilityPrompt, String tierInstruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位英语润色助手。根据学生当前能力水平和目标档次，对指定句子进行润色。\n");
        if (abilityPrompt != null && !abilityPrompt.isBlank()) {
            sb.append('\n').append(abilityPrompt).append('\n');
        }
        sb.append("\n润色档次要求: ").append(tierInstruction).append('\n');
        sb.append("""

                请提供 2 个不同的润色版本，每个版本用不同的改写思路。
                explanation 用中文简要说明改了什么、为什么这样改（30字以内）。

                只输出合法 JSON：
                {"candidates":[{"polished":"润色版本1","explanation":"中文解释1"},{"polished":"润色版本2","explanation":"中文解释2"}]}
                """);
        return sb.toString();
    }

    private String buildSingleUserPrompt(PolishRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("需润色的句子: ").append(request.getOriginal());
        if (request.getContext() != null && !request.getContext().isBlank()) {
            sb.append("\n上下文: ").append(request.getContext());
        }
        if (request.getReason() != null && !request.getReason().isBlank()) {
            sb.append("\n改进原因: ").append(request.getReason());
        }
        return sb.toString();
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
                for (JsonNode c : candidatesNode) {
                    String polished = c.path("polished").asText(null);
                    String explanation = c.path("explanation").asText(null);
                    if (polished != null && !polished.isBlank()) {
                        candidates.add(new PolishResponse.Candidate(polished, explanation));
                    }
                }
                if (!candidates.isEmpty()) {
                    return new PolishResponse(candidates);
                }
            }

            String polished = node.has("polished") ? node.get("polished").asText() : null;
            String explanation = node.has("explanation") ? node.get("explanation").asText() : null;
            return new PolishResponse(polished, explanation);
        } catch (Exception e) {
            log.warn("[POLISH] traceId={} failed to parse AI response: {}", traceId, raw, e);
            return new PolishResponse(null, "AI 返回格式异常，请重试");
        }
    }

    // ── 全文润色 prompt / parse ──

    private String buildEssaySystemPrompt(String abilityPrompt, String tierInstruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位英语润色助手。逐句润色学生作文中的每一句话。\n");
        if (abilityPrompt != null && !abilityPrompt.isBlank()) {
            sb.append('\n').append(abilityPrompt).append('\n');
        }
        sb.append("\n润色档次要求: ").append(tierInstruction).append('\n');
        sb.append("""

                规则：
                1. 先对作文整体进行分析，输出 summary 字段：
                   - strengths: 列出 2~3 个作文做得好的方面（中文，每条 20 字以内）
                   - improvements: 列出 2~3 个要达到当前润色档次需要改进的方面（中文，每条 20 字以内）
                2. 将作文按句子拆分（以 . ! ? 结尾为一句）
                3. 对每一句提供一个润色版本
                4. 如果某句已经很好不需要改动，polished 直接输出原句
                5. explanation 用中文说明改了什么、为什么改（50字以内）
                6. original 必须是作文中的原文精确子串

                只输出合法 JSON：
                {"summary":{"strengths":["优点1","优点2"],"improvements":["改进方向1","改进方向2"]},"sentences":[{"original":"原句1","polished":"润色后1","explanation":"中文解释1"}]}
                """);
        return sb.toString();
    }

    private PolishEssayResponse parseEssayResponse(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[POLISH-ESSAY] traceId={} empty response", traceId);
            return new PolishEssayResponse(List.of());
        }

        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));

            // Parse summary
            PolishEssayResponse.Summary summary = null;
            JsonNode summaryNode = node.path("summary");
            if (summaryNode.isObject()) {
                List<String> strengths = new ArrayList<>();
                List<String> improvements = new ArrayList<>();
                JsonNode sArr = summaryNode.path("strengths");
                if (sArr.isArray()) {
                    for (JsonNode item : sArr) strengths.add(item.asText());
                }
                JsonNode iArr = summaryNode.path("improvements");
                if (iArr.isArray()) {
                    for (JsonNode item : iArr) improvements.add(item.asText());
                }
                if (!strengths.isEmpty() || !improvements.isEmpty()) {
                    summary = new PolishEssayResponse.Summary(strengths, improvements);
                }
            }

            // Parse sentences
            JsonNode arr = node.path("sentences");
            if (!arr.isArray()) {
                log.warn("[POLISH-ESSAY] traceId={} no sentences array", traceId);
                return new PolishEssayResponse(summary, List.of());
            }

            List<PolishEssayResponse.SentencePolish> items = new ArrayList<>();
            for (JsonNode s : arr) {
                String original = s.path("original").asText(null);
                String polished = s.path("polished").asText(null);
                String explanation = s.path("explanation").asText(null);
                if (original != null && !original.isBlank() && polished != null && !polished.isBlank()) {
                    items.add(new PolishEssayResponse.SentencePolish(original.trim(), polished.trim(), explanation));
                }
            }
            return new PolishEssayResponse(summary, items);
        } catch (Exception e) {
            log.warn("[POLISH-ESSAY] traceId={} parse failed: {}", traceId, raw, e);
            return new PolishEssayResponse(List.of());
        }
    }
}
