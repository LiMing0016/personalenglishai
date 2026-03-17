package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.dto.writing.WritingModelEssayRequest;
import com.personalenglishai.backend.dto.writing.WritingModelEssayResponse;
import com.personalenglishai.backend.service.rubric.RubricService;
import com.personalenglishai.backend.service.rubric.RubricTextBuilder;
import com.personalenglishai.backend.service.writing.WritingModelEssayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WritingModelEssayServiceImpl implements WritingModelEssayService {

    private static final Logger log = LoggerFactory.getLogger(WritingModelEssayServiceImpl.class);
    private static final String MODEL = "gpt-4o-mini";

    private final OpenAiClient openAiClient;
    private final RubricService rubricService;
    private final RubricTextBuilder rubricTextBuilder;
    private final ObjectMapper objectMapper;

    public WritingModelEssayServiceImpl(OpenAiClient openAiClient,
                                        RubricService rubricService,
                                        RubricTextBuilder rubricTextBuilder,
                                        ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.rubricService = rubricService;
        this.rubricTextBuilder = rubricTextBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public WritingModelEssayResponse generate(WritingModelEssayRequest request) {
        String traceId = "model-essay-" + UUID.randomUUID().toString().substring(0, 8);
        String stage = rubricService.normalizeStage(request.getStudyStage());
        String mode = rubricService.normalizeMode(request.getWritingMode());
        RubricActiveResponse activeRubric = rubricService.getActiveRubric(stage, mode);
        String rubricKey = activeRubric == null ? nullIfBlank(stage + "-" + mode) : activeRubric.getRubricKey();
        String rubricText = rubricTextBuilder.buildRubricText(stage, mode);
        String resolvedTopicContent = resolveTopicContent(request);
        String normalizedTaskPrompt = nullIfBlank(request.getTaskPrompt());

        log.info("[WRITING-MODEL-ESSAY] traceId={} userId={} stage={} mode={} taskType={} topicLen={} taskPromptLen={} essayLen={}",
                traceId,
                request.getUserId(),
                stage,
                mode,
                normalize(request.getTaskType()),
                resolvedTopicContent == null ? 0 : resolvedTopicContent.length(),
                normalizedTaskPrompt == null ? 0 : normalizedTaskPrompt.length(),
                request.getEssay() == null ? 0 : request.getEssay().length());

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(stage, mode, rubricKey, rubricText, resolvedTopicContent, normalizedTaskPrompt, request);

        long start = System.currentTimeMillis();
        String raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId, MODEL, 0.5, 4096);
        long elapsed = System.currentTimeMillis() - start;

        WritingModelEssayResponse response = parseResponse(raw, traceId);
        response.setRubricKey(rubricKey);
        response.setMode(mode);
        response.setStage(stage);
        response.setTopicContent(resolvedTopicContent);
        response.setTaskPrompt(normalizedTaskPrompt);

        log.info("[WRITING-MODEL-ESSAY] traceId={} excellentLen={} perfectLen={} elapsed={}ms",
                traceId,
                response.getExcellentEssay() == null || response.getExcellentEssay().getEssay() == null ? 0 : response.getExcellentEssay().getEssay().length(),
                response.getPerfectEssay() == null || response.getPerfectEssay().getEssay() == null ? 0 : response.getPerfectEssay().getEssay().length(),
                elapsed);
        return response;
    }

    private String buildSystemPrompt() {
        return """
                你是一位英语写作教练，负责根据当前学段与评分 rubric 生成“范文”。

                你的任务是针对同一题目或同一主题，输出两篇完整英文作文：
                1. 优秀作文：稳定高分范文
                2. 满分作文：按当前 rubric 最高档特征生成的范文

                同时，每篇作文都必须带两组中文学习说明：
                1. highScoreReasons：解释这篇作文为什么能拿高分，必须明确对应评分维度，2~4条
                2. improvementGuidance：对照学生当前作文，指出学生还缺哪些动作、内容、结构或表达，2~4条

                生成规则：
                - 英文范文必须是完整作文
                - 中文说明必须具体，不要空泛
                - “你该怎么做”必须对照学生当前作文，不要只讲套路
                - exam 模式必须严格围绕题目内容和写作要求生成
                - free 模式若题目内容为空，请先从学生当前作文中提炼中心主题，再基于该主题生成两篇范文
                - 优秀作文与满分作文不要只是同一篇的小改版，应该有清晰档位差异
                - 若给了字数范围，范文应尽量落在该范围内；若给了推荐上限，则优先靠近上限

                输出 JSON 结构固定如下：
                {
                  "excellentEssay": {
                    "label": "优秀作文",
                    "essay": "完整英文作文",
                    "summary": "中文简述",
                    "highScoreReasons": ["..."],
                    "improvementGuidance": ["..."]
                  },
                  "perfectEssay": {
                    "label": "满分作文",
                    "essay": "完整英文作文",
                    "summary": "中文简述",
                    "highScoreReasons": ["..."],
                    "improvementGuidance": ["..."]
                  }
                }

                严格要求：
                - 只输出合法 JSON
                - 不要输出 markdown
                - 不要输出额外解释
                - 两篇作文都必须存在
                """;
    }

    private String buildUserPrompt(String stage,
                                   String mode,
                                   String rubricKey,
                                   String rubricText,
                                   String topicContent,
                                   String taskPrompt,
                                   WritingModelEssayRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("[WRITING_CONTEXT]\n");
        sb.append("study_stage=").append(stage).append('\n');
        sb.append("writing_mode=").append(mode).append('\n');
        sb.append("task_type=").append(normalize(request.getTaskType())).append('\n');
        sb.append("rubric_key=").append(normalize(rubricKey)).append('\n');
        if (request.getMinWords() != null || request.getRecommendedMaxWords() != null) {
            sb.append("word_range=");
            if (request.getMinWords() != null) {
                sb.append(request.getMinWords());
            } else {
                sb.append("unknown");
            }
            sb.append('-');
            if (request.getRecommendedMaxWords() != null) {
                sb.append(request.getRecommendedMaxWords());
            } else {
                sb.append("unknown");
            }
            sb.append('\n');
        }
        sb.append('\n');

        sb.append("[TOPIC_CONTENT]\n");
        if (topicContent != null) {
            sb.append(topicContent).append('\n');
        } else {
            sb.append("(empty)\n");
        }
        sb.append('\n');

        sb.append("[TASK_PROMPT]\n");
        if (taskPrompt != null) {
            sb.append(taskPrompt).append('\n');
        } else {
            sb.append("(empty)\n");
        }
        sb.append('\n');

        sb.append("[STUDENT_ESSAY]\n");
        sb.append(request.getEssay().trim()).append('\n').append('\n');

        sb.append("[RUBRIC_FROM_DB]\n");
        sb.append(rubricText == null ? "" : rubricText).append('\n').append('\n');

        sb.append("[OUTPUT_REQUIREMENTS]\n");
        sb.append("- excellentEssay 表示稳定高分参考范文。\n");
        sb.append("- perfectEssay 表示按最高档特征生成的参考范文。\n");
        sb.append("- highScoreReasons 每篇 2~4 条，中文，必须明确对应 rubric 维度。\n");
        sb.append("- improvementGuidance 每篇 2~4 条，中文，必须对照学生当前作文指出差距。\n");
        if ("free".equals(mode) && topicContent == null) {
            sb.append("- 当前是自由写作且没有明确题目，请先从学生作文中提炼中心主题，再基于同一主题生成两篇范文。\n");
        }
        return sb.toString();
    }

    private WritingModelEssayResponse parseResponse(String raw, String traceId) {
        WritingModelEssayResponse response = emptyResponse();
        if (raw == null || raw.isBlank()) {
            log.warn("[WRITING-MODEL-ESSAY] traceId={} empty response", traceId);
            return response;
        }
        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            response.setExcellentEssay(readCard(node.path("excellentEssay"), "优秀作文"));
            response.setPerfectEssay(readCard(node.path("perfectEssay"), "满分作文"));
            return response;
        } catch (Exception e) {
            log.warn("[WRITING-MODEL-ESSAY] traceId={} parse failed raw={}", traceId, raw, e);
            return response;
        }
    }

    private WritingModelEssayResponse.ModelEssayCard readCard(JsonNode node, String fallbackLabel) {
        WritingModelEssayResponse.ModelEssayCard card = new WritingModelEssayResponse.ModelEssayCard();
        card.setLabel(nullIfBlank(node.path("label").asText(null)) == null ? fallbackLabel : nullIfBlank(node.path("label").asText(null)));
        card.setEssay(nullIfBlank(node.path("essay").asText(null)));
        card.setSummary(nullIfBlank(node.path("summary").asText(null)));
        card.setHighScoreReasons(readStringList(node.path("highScoreReasons")));
        card.setImprovementGuidance(readStringList(node.path("improvementGuidance")));
        return card;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return items;
        }
        for (JsonNode item : node) {
            String text = nullIfBlank(item.asText(null));
            if (text != null) {
                items.add(text);
            }
        }
        return items;
    }

    private WritingModelEssayResponse emptyResponse() {
        WritingModelEssayResponse response = new WritingModelEssayResponse();
        WritingModelEssayResponse.ModelEssayCard excellent = new WritingModelEssayResponse.ModelEssayCard();
        excellent.setLabel("优秀作文");
        excellent.setHighScoreReasons(List.of());
        excellent.setImprovementGuidance(List.of());
        WritingModelEssayResponse.ModelEssayCard perfect = new WritingModelEssayResponse.ModelEssayCard();
        perfect.setLabel("满分作文");
        perfect.setHighScoreReasons(List.of());
        perfect.setImprovementGuidance(List.of());
        response.setExcellentEssay(excellent);
        response.setPerfectEssay(perfect);
        return response;
    }

    private String resolveTopicContent(WritingModelEssayRequest request) {
        String topicContent = nullIfBlank(request.getTopicContent());
        if (topicContent != null) {
            return topicContent;
        }
        if ("free".equals(rubricService.normalizeMode(request.getWritingMode()))) {
            return null;
        }
        return nullIfBlank(request.getTaskPrompt());
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
