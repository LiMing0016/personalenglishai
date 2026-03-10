package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.WritingTemplateRequest;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse.KeyExpression;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse.ParagraphTemplate;
import com.personalenglishai.backend.dto.writing.WritingTemplateResponse.TemplateItem;
import com.personalenglishai.backend.service.writing.WritingTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WritingTemplateServiceImpl implements WritingTemplateService {

    private static final Logger log = LoggerFactory.getLogger(WritingTemplateServiceImpl.class);
    private static final String TEMPLATE_MODEL = "gpt-4o-mini";

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public WritingTemplateServiceImpl(OpenAiClient openAiClient,
                                      ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WritingTemplateResponse extract(WritingTemplateRequest request) {
        String traceId = "template-" + UUID.randomUUID().toString().substring(0, 8);
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request);

        log.info("[WRITING-TEMPLATE] traceId={} userId={} stage={} mode={} textLen={} hasTaskPrompt={}",
                traceId,
                request.getUserId(),
                normalize(request.getStudyStage()),
                normalize(request.getWritingMode()),
                request.getText() == null ? 0 : request.getText().length(),
                request.getTaskPrompt() != null && !request.getTaskPrompt().isBlank());

        long start = System.currentTimeMillis();
        String raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId,
                TEMPLATE_MODEL, 0.3, 8192);
        long elapsed = System.currentTimeMillis() - start;

        WritingTemplateResponse response = parseResponse(raw, traceId);
        int paraCount = response.getParagraphs() == null ? 0 : response.getParagraphs().size();
        log.info("[WRITING-TEMPLATE] traceId={} paragraphs={} elapsed={}ms",
                traceId, paraCount, elapsed);
        return response;
    }

    private String buildSystemPrompt() {
        return """
                你是一位资深英语写作教练。

                你的任务是按段落顺序分析学生作文，从每一段中提炼可复用的写作模版，并帮助学生积累优秀表达。

                任务步骤：
                1. 按作文的实际段落顺序逐段分析（第1段、第2段…）。
                2. 识别每一段的写作功能，例如：引入话题、描述图画、提出论点、解释原因、举例论证、让步转折、总结观点、升华主题。
                3. 用一句中文概括该段主要作用。
                4. 从该段提炼 1~5 个可复用的英文句式模版。
                5. 从该段提取 0~6 个值得积累的高级表达或搭配。
                6. 如果模板中包含占位符（例如 [topic] [reason] [example] [benefit] [opinion] [character] 等），
                   请为每个占位符提供 2~5 个自然可替换的示例词或短语。

                根据学段调整输出难度：
                - 初中：模版句式简洁，用词基础，替换示例贴近日常生活
                - 高中：模版有适当复杂结构（定语从句、倒装等），替换示例偏考试话题
                - 大学/考研：模版结构较复杂（独立主格、虚拟语气等），替换示例偏学术/社会议题
                - 如未提供学段，默认按高中水平输出

                模版提炼规则：
                - 模版必须是英文
                - 模版应自然、简洁、可直接用于考试写作
                - 使用占位符替代具体内容，例如：[topic] [reason] [example] [benefit] [opinion] [character] [result]
                - 不要整句照搬原文，要抽象成通用表达结构
                - 优先提炼结构清晰、可替换性强的句式
                - 如句型具有学习价值，可以保留完整句型，不必刻意压缩长度

                表达提取规则：
                每个表达包含：
                - expression: 英文表达或搭配
                - usage: 中文用法说明（15字以内）
                - usageTips: 2~3 条简短使用建议（每条20字以内）

                段落分析规则：
                - 按文章实际段落顺序逐段分析
                - paragraphIndex 从 1 开始
                - 输出所有有效段落，不得遗漏结尾段

                输出字段说明：
                - essayType: 中文体裁（议论文/图画作文/书信/图表描述/说明文等）
                - paragraphs: 数组，每个元素：
                  - paragraphIndex: 段落序号
                  - function: 段落功能（中文）
                  - summary: 一句话概括（中文，15字以内）
                  - templates: 模版数组，每个元素：
                    - template: 英文句式模版
                    - placeholders: 占位符替换示例，格式 { "placeholderName": ["example1", "example2", ...] }
                  - keyExpressions: 表达数组，每个元素：
                    - expression: 英文表达
                    - usage: 中文用法说明（15字以内）
                    - usageTips: 中文使用建议数组（2~3条，每条20字以内）
                - usageTips: 2~3 条全局中文使用建议（每条20字以内）

                严格要求：
                - 模版必须是英文，所有说明必须是中文
                - JSON 字段名称必须完全一致
                - 不要输出解释文本，不要输出 markdown
                - 只输出合法 JSON
                """;
    }

    private String buildUserPrompt(WritingTemplateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("学段: ").append(normalize(request.getStudyStage())).append('\n');
        sb.append("写作模式: ").append(normalize(request.getWritingMode())).append('\n');
        if (request.getTaskPrompt() != null && !request.getTaskPrompt().isBlank()) {
            sb.append("题目要求:\n").append(request.getTaskPrompt().trim()).append("\n\n");
        }
        sb.append("作文正文:\n").append(request.getText().trim());
        return sb.toString();
    }

    // ── Response Parsing ──

    private WritingTemplateResponse parseResponse(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[WRITING-TEMPLATE] traceId={} empty response", traceId);
            return emptyResponse();
        }

        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            WritingTemplateResponse response = new WritingTemplateResponse();
            response.setEssayType(nullIfBlank(node.path("essayType").asText(null)));
            response.setParagraphs(readParagraphs(node.path("paragraphs")));
            response.setUsageTips(readStringList(node.path("usageTips")));
            return response;
        } catch (Exception e) {
            log.warn("[WRITING-TEMPLATE] traceId={} parse failed raw={}", traceId, raw, e);
            return emptyResponse();
        }
    }

    private List<ParagraphTemplate> readParagraphs(JsonNode node) {
        List<ParagraphTemplate> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode item : node) {
            ParagraphTemplate pt = new ParagraphTemplate();
            pt.setParagraphIndex(item.path("paragraphIndex").asInt(list.size() + 1));
            pt.setFunction(nullIfBlank(item.path("function").asText(null)));
            pt.setSummary(nullIfBlank(item.path("summary").asText(null)));
            pt.setTemplates(readTemplateItems(item.path("templates")));
            pt.setKeyExpressions(readKeyExpressions(item.path("keyExpressions")));
            list.add(pt);
        }
        return list;
    }

    private List<TemplateItem> readTemplateItems(JsonNode node) {
        List<TemplateItem> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode item : node) {
            TemplateItem ti = new TemplateItem();
            ti.setTemplate(nullIfBlank(item.path("template").asText(null)));
            if (ti.getTemplate() == null) continue;

            JsonNode phNode = item.path("placeholders");
            if (phNode != null && phNode.isObject()) {
                Map<String, List<String>> placeholders = new LinkedHashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = phNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    placeholders.put(entry.getKey(), readStringList(entry.getValue()));
                }
                if (!placeholders.isEmpty()) {
                    ti.setPlaceholders(placeholders);
                }
            }
            list.add(ti);
        }
        return list;
    }

    private List<KeyExpression> readKeyExpressions(JsonNode node) {
        List<KeyExpression> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode item : node) {
            String expr = nullIfBlank(item.path("expression").asText(null));
            if (expr == null) continue;
            KeyExpression ke = new KeyExpression();
            ke.setExpression(expr);
            ke.setUsage(nullIfBlank(item.path("usage").asText(null)));
            ke.setUsageTips(readStringList(item.path("usageTips")));
            list.add(ke);
        }
        return list;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node == null || !node.isArray()) return items;
        for (JsonNode item : node) {
            String text = nullIfBlank(item.asText(null));
            if (text != null) items.add(text);
        }
        return items;
    }

    // ── Utilities ──

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

    private WritingTemplateResponse emptyResponse() {
        WritingTemplateResponse response = new WritingTemplateResponse();
        response.setParagraphs(List.of());
        response.setUsageTips(List.of());
        return response;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
