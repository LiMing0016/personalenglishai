package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.prompt.AbilityPromptBuilder;
import com.personalenglishai.backend.dto.writing.PolishRequest;
import com.personalenglishai.backend.dto.writing.PolishResponse;
import com.personalenglishai.backend.entity.UserAbilityProfile;
import com.personalenglishai.backend.mapper.UserAbilityProfileMapper;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    @Override
    public PolishResponse polish(PolishRequest request) {
        Long userId = request.getUserId();
        String tier = normalizeTier(request.getTier());
        String traceId = "polish-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Load ability profile
        UserAbilityProfile profile = userId != null
                ? userAbilityProfileMapper.selectByUserId(userId)
                : null;

        // 2. Build ability prompt
        String abilityPrompt = abilityPromptBuilder.buildAbilityPrompt(profile, userId);

        // 3. Build system prompt
        String tierInstruction = TIER_INSTRUCTIONS.getOrDefault(tier, TIER_INSTRUCTIONS.get("steady"));
        String systemPrompt = buildSystemPrompt(abilityPrompt, tierInstruction);

        // 4. Build user prompt
        String userPrompt = buildUserPrompt(request);

        log.info("[POLISH] traceId={} userId={} tier={} originalLen={}",
                traceId, userId, tier, request.getOriginal().length());

        // 5. Call OpenAI
        String rawResponse = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId);

        // 6. Parse JSON response
        return parseResponse(rawResponse, traceId);
    }

    private String normalizeTier(String tier) {
        if (tier == null) return "steady";
        String t = tier.trim().toLowerCase();
        return TIER_INSTRUCTIONS.containsKey(t) ? t : "steady";
    }

    private String buildSystemPrompt(String abilityPrompt, String tierInstruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位英语润色助手。根据学生当前能力水平和目标档次，对指定表达进行润色。\n");
        if (abilityPrompt != null && !abilityPrompt.isBlank()) {
            sb.append('\n').append(abilityPrompt).append('\n');
        }
        sb.append("\n润色档次要求: ").append(tierInstruction).append('\n');
        sb.append("\n只输出合法 JSON：{\"polished\":\"润色后文本\",\"explanation\":\"中文解释\"}");
        return sb.toString();
    }

    private String buildUserPrompt(PolishRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("需润色的表达: ").append(request.getOriginal());
        if (request.getContext() != null && !request.getContext().isBlank()) {
            sb.append("\n上下文: ").append(request.getContext());
        }
        if (request.getReason() != null && !request.getReason().isBlank()) {
            sb.append("\n改进原因: ").append(request.getReason());
        }
        return sb.toString();
    }

    private PolishResponse parseResponse(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[POLISH] traceId={} empty response from AI", traceId);
            return new PolishResponse(null, "AI 未返回有效结果");
        }

        try {
            // Strip markdown code fences if present
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                int firstNewline = cleaned.indexOf('\n');
                int lastFence = cleaned.lastIndexOf("```");
                if (firstNewline > 0 && lastFence > firstNewline) {
                    cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
                }
            }

            JsonNode node = objectMapper.readTree(cleaned);
            String polished = node.has("polished") ? node.get("polished").asText() : null;
            String explanation = node.has("explanation") ? node.get("explanation").asText() : null;
            return new PolishResponse(polished, explanation);
        } catch (Exception e) {
            log.warn("[POLISH] traceId={} failed to parse AI response: {}", traceId, raw, e);
            return new PolishResponse(null, "AI 返回格式异常，请重试");
        }
    }
}
