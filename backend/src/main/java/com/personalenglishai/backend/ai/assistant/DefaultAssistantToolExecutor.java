package com.personalenglishai.backend.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.context.AIContext;
import com.personalenglishai.backend.ai.context.RequestContext;
import com.personalenglishai.backend.ai.dto.AICommandRequest;
import com.personalenglishai.backend.dto.writing.PolishRequest;
import com.personalenglishai.backend.dto.writing.PolishResponse;
import com.personalenglishai.backend.dto.writing.TranslateRequest;
import com.personalenglishai.backend.dto.writing.TranslateResponse;
import com.personalenglishai.backend.dto.writing.WritingMaterialRequest;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse;
import com.personalenglishai.backend.dto.writing.WritingModelEssayRequest;
import com.personalenglishai.backend.dto.writing.WritingModelEssayResponse;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentScoreSummary;
import com.personalenglishai.backend.mapper.DocumentScoreSummaryMapper;
import com.personalenglishai.backend.service.document.DocumentService;
import com.personalenglishai.backend.service.writing.WritingMaterialService;
import com.personalenglishai.backend.service.writing.WritingModelEssayService;
import com.personalenglishai.backend.service.writing.WritingPolishService;
import com.personalenglishai.backend.service.writing.WritingTranslateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultAssistantToolExecutor implements AssistantToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultAssistantToolExecutor.class);

    private final ObjectMapper objectMapper;
    private final WritingPolishService writingPolishService;
    private final WritingTranslateService writingTranslateService;
    private final WritingMaterialService writingMaterialService;
    private final WritingModelEssayService writingModelEssayService;
    private final DocumentService documentService;
    private final DocumentScoreSummaryMapper documentScoreSummaryMapper;
    private final OpenAiClient openAiClient;

    public DefaultAssistantToolExecutor(ObjectMapper objectMapper,
                                        WritingPolishService writingPolishService,
                                        WritingTranslateService writingTranslateService,
                                        WritingMaterialService writingMaterialService,
                                        WritingModelEssayService writingModelEssayService,
                                        DocumentService documentService,
                                        DocumentScoreSummaryMapper documentScoreSummaryMapper,
                                        OpenAiClient openAiClient) {
        this.objectMapper = objectMapper;
        this.writingPolishService = writingPolishService;
        this.writingTranslateService = writingTranslateService;
        this.writingMaterialService = writingMaterialService;
        this.writingModelEssayService = writingModelEssayService;
        this.documentService = documentService;
        this.documentScoreSummaryMapper = documentScoreSummaryMapper;
        this.openAiClient = openAiClient;
    }

    @Override
    public AssistantToolResult execute(String toolName,
                                       String argumentsJson,
                                       AICommandRequest request,
                                       RequestContext ctx,
                                       AIContext aiContext) {
        try {
            return switch (toolName) {
                case "polish_selection" -> polishSelection(argumentsJson, ctx);
                case "rewrite_selection" -> rewriteSelection(argumentsJson, request, ctx, aiContext);
                case "translate_selection" -> translateSelection(argumentsJson, ctx);
                case "get_model_essay" -> getModelEssay(argumentsJson, request, ctx, aiContext);
                case "get_writing_material" -> getWritingMaterial(argumentsJson, request, ctx, aiContext);
                case "get_score_summary" -> getScoreSummary(argumentsJson, request, ctx);
                default -> new AssistantToolResult(
                        "{\"ok\":false,\"error\":\"unsupported tool\"}",
                        "暂不支持该工具"
                );
            };
        } catch (Exception e) {
            log.warn("assistant tool failed tool={} error={}", toolName, e.getMessage(), e);
            return new AssistantToolResult(
                    "{\"ok\":false,\"error\":\"tool execution failed\"}",
                    "工具执行失败"
            );
        }
    }

    private AssistantToolResult polishSelection(String argumentsJson, RequestContext ctx) throws Exception {
        JsonNode args = readArgs(argumentsJson);
        String selectedText = text(args, "selectedText");
        if (isBlank(selectedText)) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"selectedText required\"}", "缺少选中文本");
        }
        PolishRequest request = new PolishRequest();
        request.setOriginal(selectedText);
        request.setContext(text(args, "context"));
        request.setReason(text(args, "reason"));
        request.setTier(defaultIfBlank(text(args, "tier"), "steady"));
        request.setUserId(ctx.getUserId());

        PolishResponse response = writingPolishService.polish(request);
        String polished = firstCandidate(response);
        return new AssistantToolResult(
                objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "polished", polished,
                        "explanation", response.getExplanation()
                )),
                "已生成润色版本"
        );
    }

    private AssistantToolResult rewriteSelection(String argumentsJson,
                                                 AICommandRequest request,
                                                 RequestContext ctx,
                                                 AIContext aiContext) throws Exception {
        JsonNode args = readArgs(argumentsJson);
        String selectedText = text(args, "selectedText");
        if (isBlank(selectedText)) {
            selectedText = selectedTextFromRequest(request);
        }
        if (isBlank(selectedText)) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"selectedText required\"}", "缺少选中文本");
        }

        String instruction = defaultIfBlank(text(args, "instruction"), safeText(request.getInstruction()));
        String systemPrompt = """
                你是一位英语改写助手。
                请根据用户要求改写给定英文文本，保持原意，输出合法 JSON：
                {"rewritten":"改写后的英文","summary":"中文简述"}
                不要输出 markdown。
                """;
        String userPrompt = "instruction:\n" + instruction
                + "\n\nselected_text:\n" + selectedText
                + "\n\ncontext:\n" + firstNonBlank(text(args, "context"), aiContext == null ? null : aiContext.getDraftContent());
        String raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, ctx.getRequestId());
        JsonNode root = objectMapper.readTree(stripCodeFences(raw));
        String rewritten = defaultIfBlank(text(root, "rewritten"), selectedText);
        String summary = defaultIfBlank(text(root, "summary"), "已按要求改写选中文本");
        return new AssistantToolResult(
                objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "rewritten", rewritten,
                        "summary", summary
                )),
                summary
        );
    }

    private AssistantToolResult translateSelection(String argumentsJson, RequestContext ctx) throws Exception {
        JsonNode args = readArgs(argumentsJson);
        String selectedText = text(args, "selectedText");
        if (isBlank(selectedText)) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"selectedText required\"}", "缺少选中文本");
        }
        TranslateRequest request = new TranslateRequest();
        request.setText(selectedText);
        request.setMode(defaultIfBlank(text(args, "mode"), "full"));
        request.setUserId(ctx.getUserId());
        TranslateResponse response = writingTranslateService.translate(request);
        String translation = response.getTranslation();
        if (isBlank(translation) && response.getSentences() != null && !response.getSentences().isEmpty()) {
            translation = response.getSentences().get(0).getChinese();
        }
        return new AssistantToolResult(
                objectMapper.writeValueAsString(Map.of(
                        "ok", true,
                        "translation", defaultIfBlank(translation, "")
                )),
                "已翻译选中文本"
        );
    }

    private AssistantToolResult getModelEssay(String argumentsJson,
                                              AICommandRequest request,
                                              RequestContext ctx,
                                              AIContext aiContext) throws Exception {
        JsonNode args = readArgs(argumentsJson);
        WritingModelEssayRequest modelEssayRequest = new WritingModelEssayRequest();
        modelEssayRequest.setEssay(firstNonBlank(text(args, "essay"), safeDraft(aiContext)));
        modelEssayRequest.setStudyStage(text(args, "studyStage"));
        modelEssayRequest.setWritingMode(text(args, "writingMode"));
        modelEssayRequest.setTaskType(text(args, "taskType"));
        modelEssayRequest.setTopicContent(text(args, "topicContent"));
        modelEssayRequest.setTaskPrompt(firstNonBlank(text(args, "taskPrompt"), taskPromptFromRequest(request)));
        modelEssayRequest.setMinWords(integer(args, "minWords"));
        modelEssayRequest.setRecommendedMaxWords(integer(args, "recommendedMaxWords"));
        modelEssayRequest.setUserId(ctx.getUserId());

        WritingModelEssayResponse response = writingModelEssayService.generate(modelEssayRequest);
        return new AssistantToolResult(
                objectMapper.writeValueAsString(response),
                "已读取范文参考"
        );
    }

    private AssistantToolResult getWritingMaterial(String argumentsJson,
                                                   AICommandRequest request,
                                                   RequestContext ctx,
                                                   AIContext aiContext) throws Exception {
        JsonNode args = readArgs(argumentsJson);
        WritingMaterialRequest materialRequest = new WritingMaterialRequest();
        materialRequest.setTaskPrompt(firstNonBlank(text(args, "taskPrompt"), taskPromptFromRequest(request)));
        materialRequest.setEssayText(firstNonBlank(text(args, "essayText"), safeDraft(aiContext)));
        materialRequest.setStudyStage(text(args, "studyStage"));
        materialRequest.setWritingMode(text(args, "writingMode"));
        materialRequest.setUserId(ctx.getUserId());

        if (isBlank(materialRequest.getTaskPrompt())) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"taskPrompt required\"}", "缺少题目要求");
        }

        WritingMaterialResponse response = writingMaterialService.generate(materialRequest);
        return new AssistantToolResult(
                objectMapper.writeValueAsString(response),
                "已整理写作素材"
        );
    }

    private AssistantToolResult getScoreSummary(String argumentsJson,
                                                AICommandRequest request,
                                                RequestContext ctx) throws Exception {
        JsonNode args = readArgs(argumentsJson);
        String docId = firstNonBlank(text(args, "docId"), request.getContextRefs() == null ? null : request.getContextRefs().getDocId());
        if (isBlank(docId)) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"docId required\"}", "缺少作文文档");
        }
        Document document = documentService.findByPublicId(ctx.getTenantId(), ctx.getWorkspaceId(), docId);
        if (document == null) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"document not found\"}", "找不到这篇作文");
        }
        DocumentScoreSummary summary = documentScoreSummaryMapper.selectByDocumentId(document.getId());
        if (summary == null) {
            return new AssistantToolResult("{\"ok\":false,\"error\":\"score summary not found\"}", "当前作文还没有评分概览");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("latestOverallScore", summary.getLatestOverallScore());
        payload.put("bestOverallScore", summary.getBestOverallScore());
        payload.put("latestBandLabel", summary.getLatestBandLabel());
        payload.put("latestWordCount", summary.getLatestWordCount());
        payload.put("latestTotalErrorCount", summary.getLatestTotalErrorCount());
        payload.put("latestMajorErrorCount", summary.getLatestMajorErrorCount());
        payload.put("latestMinorErrorCount", summary.getLatestMinorErrorCount());
        return new AssistantToolResult(
                objectMapper.writeValueAsString(payload),
                "已读取最近一次评分结果"
        );
    }

    private JsonNode readArgs(String argumentsJson) throws Exception {
        if (isBlank(argumentsJson)) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(argumentsJson);
    }

    private String firstCandidate(PolishResponse response) {
        if (response == null) {
            return "";
        }
        if (response.getCandidates() != null && !response.getCandidates().isEmpty()) {
            return defaultIfBlank(response.getCandidates().get(0).getPolished(), "");
        }
        return defaultIfBlank(response.getPolished(), "");
    }

    private String taskPromptFromRequest(AICommandRequest request) {
        if (request == null || request.getConstraints() == null) {
            return null;
        }
        Object value = request.getConstraints().get("taskPrompt");
        return value == null ? null : String.valueOf(value).trim();
    }

    private String selectedTextFromRequest(AICommandRequest request) {
        if (request == null || request.getConstraints() == null) {
            return null;
        }
        Object value = request.getConstraints().get("selectedText");
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer integer(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isInt() ? value.asInt() : null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred.trim();
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String safeDraft(AIContext aiContext) {
        return aiContext == null ? "" : safeText(aiContext.getDraftContent());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
}
