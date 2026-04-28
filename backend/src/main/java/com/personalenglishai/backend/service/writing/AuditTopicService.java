package com.personalenglishai.backend.service.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import com.personalenglishai.backend.dto.writing.RecognizeTopicImageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuditTopicService {

    private static final Logger log = LoggerFactory.getLogger(AuditTopicService.class);
    private static final Set<String> ALLOWED_STATUS = Set.of("complete", "need_more_info", "invalid");
    private static final Set<String> ALLOWED_PROMPT_TYPES = Set.of("general", "material", "chart", "comic", "unknown");
    private static final Set<String> ALLOWED_GENRES = Set.of("书信", "议论文", "说明文", "演讲稿", "看图作文", "通知", "日记");
    private static final Set<String> ALLOWED_TARGET_STYLES = Set.of("exam", "free", "unknown");
    private static final Set<String> ALLOWED_NEXT_ACTIONS = Set.of("ask_user", "generate_prompt", "generate_attachment", "wait_user_upload");
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of("none", "image", "chart", "material_text");
    private static final Set<String> ALLOWED_ATTACHMENT_SOURCES = Set.of("none", "user_upload", "agent_generate", "user_text");
    private static final Pattern WORD_RANGE_PATTERN = Pattern.compile("^\\d+(\\s*-\\s*\\d+)?$");
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是写作题目设计助手。你的任务是根据用户输入，整理作文题目需求，并输出结构化 JSON。

            如果提供了当前学段，它不是普通背景信息，而是命题风格硬约束。
            你必须按该学段对应的真实考试写作语气、题面结构和字数习惯来整理题目。
            不要忽略当前学段，也不要把“当前学段”降级为可有可无的参考信息。

            必须输出的核心字段：
            1. status：complete / need_more_info / invalid
            2. topic：题目主题或正文，未知时输出 null
            3. promptType：题型，只能是 general、material、chart、comic、unknown
            4. genre：体裁，未知时输出 null
            5. wordRange：字数范围，未知时输出 null
            6. requirements：写作要求字符串，未知时输出 null
            7. message：兼容旧链路的中文提示，status=complete 时可为 null

            单 agent 扩展字段：
            8. targetStyle：exam / free / unknown
            9. needsMoreInfo：布尔值
            10. assistantReply：直接给用户显示的自然语言回复
            11. promptReady：布尔值
            12. readyReason：当前是否可继续生成题单的原因
            13. nextAction：ask_user / generate_prompt / generate_attachment / wait_user_upload
            14. needsAttachment：布尔值
            15. attachmentType：none / image / chart / material_text
            16. attachmentSource：none / user_upload / agent_generate / user_text
            17. attachmentReady：布尔值
            18. attachmentTitle：附件标题，未知时输出 null
            19. attachmentInstruction：给生成器或前端的说明，未知时输出 null
            20. attachmentPayload：对象，无内容时输出 {}

            规则：
            - 如果用户已选择体裁或字数（genre/wordRange 不为空），直接采用，不要覆盖
            - 如果用户提到“考试风格”“更像真题”，targetStyle 设为 exam；如果提到自由写作、练笔，targetStyle 设为 free
            - “图画作文”“漫画作文”优先判断为 promptType=comic；“图表作文”优先判断为 promptType=chart；“材料作文”优先判断为 promptType=material
            - 不要把“考试风格”误判成 promptType 或 genre
            - topic 必须尽量贴近原题，不要过度概括；如果用户只表达模糊写作意图，可以保留 null
            - requirements 只提取明确的写作要点，不要编造
            - 如果信息不足，不要编造 topic、genre、wordRange
            - assistantReply 必须是自然、简洁、有引导性的中文回复，不要像表单机器人
            - 如果适合帮助用户推进，可以在 assistantReply 中给出 2 到 4 个方向或例子
            - 布尔字段必须输出 true 或 false，不能留空
            - status 为 need_more_info 或 invalid 时，message 不能为空
            - 除 JSON 外不要输出任何其他内容
            """;

    public AuditTopicService(OpenAiClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public AuditTopicResponse audit(AuditTopicRequest request) {
        return audit(request, request.getAiProvider());
    }

    public AuditTopicResponse audit(AuditTopicRequest request, String aiProvider) {

        String userPrompt = buildUserPrompt(request);
        log.info("[AUDIT-TOPIC] auditing topic, provider={}, len={}", aiProvider, request.getTopic().length());

        try {
            String raw = openAiClient.callWithProvider(
                    aiProvider,
                    SYSTEM_PROMPT,
                    userPrompt,
                    "audit-topic",
                    0.1,
                    1200
            );
            return parseResponse(raw, request);
        } catch (Exception e) {
            log.error("[AUDIT-TOPIC] provider call failed, falling back: {}", e.getMessage());
            return fallback(request);
        }
    }

    private String buildUserPrompt(AuditTopicRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户输入的题目：").append(request.getTopic());
        if (request.getGenre() != null && !request.getGenre().isBlank()) {
            sb.append("\n用户已选体裁：").append(request.getGenre());
        }
        if (request.getWordRange() != null && !request.getWordRange().isBlank()) {
            sb.append("\n用户已选字数：").append(request.getWordRange());
        }
        if (request.getStudyStage() != null && !request.getStudyStage().isBlank()) {
            sb.append("\n当前学段（硬约束，必须按该学段考试风格整理）：").append(request.getStudyStage());
        }
        return sb.toString();
    }

    private AuditTopicResponse parseResponse(String raw, AuditTopicRequest request) {
        if (raw == null || raw.isBlank()) {
            return fallback(request);
        }

        try {
            String cleaned = stripCodeFences(raw);
            JsonNode node = objectMapper.readTree(cleaned);
            ParsedAuditPayload payload = validatePayload(node);

            String status = payload.status();
            String topic = normalizeTopic(payload.topic(), request.getTopic());
            String promptType = normalizePromptType(payload.promptType(), request.getTopic());
            String genre = payload.genre();
            String wordRange = payload.wordRange();
            String requirements = payload.requirements();
            String message = payload.message();
            String targetStyle = payload.targetStyle();
            Boolean needsMoreInfo = payload.needsMoreInfo();
            String assistantReply = payload.assistantReply();
            Boolean promptReady = payload.promptReady();
            String readyReason = payload.readyReason();
            String nextAction = payload.nextAction();
            Boolean needsAttachment = payload.needsAttachment();
            String attachmentType = payload.attachmentType();
            String attachmentSource = payload.attachmentSource();
            Boolean attachmentReady = payload.attachmentReady();
            String attachmentTitle = payload.attachmentTitle();
            String attachmentInstruction = payload.attachmentInstruction();
            Map<String, Object> attachmentPayload = payload.attachmentPayload();

            if ("invalid".equals(status)) {
                return AuditTopicResponse.invalid(message != null ? message : "请输入有效的作文题目");
            }

            // 优先使用用户手动选择的值
            if (request.getGenre() != null && !request.getGenre().isBlank()) {
                genre = request.getGenre();
            }
            if (request.getWordRange() != null && !request.getWordRange().isBlank()) {
                wordRange = request.getWordRange();
            }

            if ("need_more_info".equals(status)) {
                AuditTopicResponse response = AuditTopicResponse.needMoreInfo(topic, promptType, genre, wordRange, requirements, message);
                applyExtendedFields(response, payload, targetStyle, needsMoreInfo, assistantReply, promptReady,
                        readyReason, nextAction, needsAttachment, attachmentType, attachmentSource,
                        attachmentReady, attachmentTitle, attachmentInstruction, attachmentPayload);
                return response;
            }

            AuditTopicResponse response = AuditTopicResponse.complete(topic, promptType, genre, wordRange, requirements);
            applyExtendedFields(response, payload, targetStyle, needsMoreInfo, assistantReply, promptReady,
                    readyReason, nextAction, needsAttachment, attachmentType, attachmentSource,
                    attachmentReady, attachmentTitle, attachmentInstruction, attachmentPayload);
            return response;
        } catch (Exception e) {
            log.warn("[AUDIT-TOPIC] parse failed: {}", raw, e);
            return fallback(request);
        }
    }

    /** 千问不可用时的兜底：直接透传用户输入 */
    private AuditTopicResponse fallback(AuditTopicRequest request) {
        return AuditTopicResponse.complete(
                request.getTopic(),
                normalizePromptType(null, request.getTopic()),
                request.getGenre(),
                request.getWordRange(),
                request.getRequirements()
        );
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

    private String nullIfEmpty(String s) {
        if (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) return null;
        return s.trim();
    }

    private String normalizeTopic(String candidate, String original) {
        String normalizedOriginal = nullIfEmpty(original);
        String normalizedCandidate = nullIfEmpty(candidate);
        if (normalizedOriginal == null) {
            return normalizedCandidate;
        }
        if (normalizedCandidate == null) {
            return normalizedOriginal;
        }

        String compactOriginal = normalizedOriginal.replaceAll("\\s+", " ").trim();
        String compactCandidate = normalizedCandidate.replaceAll("\\s+", " ").trim();
        if (compactCandidate.equalsIgnoreCase(compactOriginal)) {
            return normalizedOriginal;
        }
        if (compactOriginal.toLowerCase().contains(compactCandidate.toLowerCase())) {
            return normalizedOriginal;
        }
        if (isOverlyGenericTopic(compactCandidate)) {
            return normalizedOriginal;
        }
        return normalizedCandidate;
    }

    private boolean isOverlyGenericTopic(String topic) {
        String normalized = topic == null ? "" : topic.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return true;
        }
        return normalized.equals("根据所给图表写一篇作文")
                || normalized.equals("根据所给图片写一篇作文")
                || normalized.equals("根据所给材料写一篇作文")
                || normalized.equals("write an essay based on the chart below")
                || normalized.equals("write an essay based on the picture below")
                || normalized.equals("write an essay based on the following drawing")
                || normalized.equals("write an essay based on the material below");
    }

    private String normalizePromptType(String promptType, String originalTopic) {
        String normalized = nullIfEmpty(promptType);
        if (normalized != null) {
            return switch (normalized.toLowerCase()) {
                case "material", "chart", "comic" -> normalized.toLowerCase();
                default -> "general";
            };
        }

        String source = originalTopic == null ? "" : originalTopic.toLowerCase();
        if (source.contains("材料") || source.contains("material")) {
            return "material";
        }
        if (source.contains("图表") || source.contains("表格") || source.contains("chart")
                || source.contains("table") || source.contains("graph")
                || source.contains("diagram") || source.contains("柱状图")
                || source.contains("折线图") || source.contains("饼图")) {
            return "chart";
        }
        if (source.contains("漫画") || source.contains("图画") || source.contains("drawing")
                || source.contains("picture") || source.contains("分镜")) {
            return "comic";
        }
        return "general";
    }

    private void applyExtendedFields(AuditTopicResponse response,
                                     ParsedAuditPayload payload,
                                     String targetStyle,
                                     Boolean needsMoreInfo,
                                     String assistantReply,
                                     Boolean promptReady,
                                     String readyReason,
                                     String nextAction,
                                     Boolean needsAttachment,
                                     String attachmentType,
                                     String attachmentSource,
                                     Boolean attachmentReady,
                                     String attachmentTitle,
                                     String attachmentInstruction,
                                     Map<String, Object> attachmentPayload) {
        response.setIsCompleteOriginalPrompt(payload.isCompleteOriginalPrompt());
        response.setShouldPreserveOriginalWording(payload.shouldPreserveOriginalWording());
        response.setIsExamStyleCompatible(payload.isExamStyleCompatible());
        response.setStyleCompatibilityReasons(payload.styleCompatibilityReasons());
        response.setMissingFields(payload.missingFields());
        response.setRequiresUserConfirmation(payload.requiresUserConfirmation());
        response.setConfirmationQuestion(payload.confirmationQuestion());
        response.setTargetStyle(targetStyle);
        response.setNeedsMoreInfo(needsMoreInfo != null ? needsMoreInfo : "need_more_info".equals(payload.status()));
        response.setAssistantReply(assistantReply != null ? assistantReply : payload.message());
        response.setPromptReady(promptReady != null ? promptReady : "complete".equals(payload.status()));
        response.setReadyReason(readyReason);
        response.setNextAction(nextAction);
        response.setNeedsAttachment(needsAttachment);
        response.setAttachmentType(attachmentType);
        response.setAttachmentSource(attachmentSource);
        response.setAttachmentReady(attachmentReady != null ? attachmentReady : !Boolean.TRUE.equals(needsAttachment));
        response.setAttachmentTitle(attachmentTitle);
        response.setAttachmentInstruction(attachmentInstruction);
        response.setAttachmentPayload(attachmentPayload);
    }

    private ParsedAuditPayload validatePayload(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("audit topic response must be a JSON object");
        }

        String status = requireEnum(node, "status", ALLOWED_STATUS);
        String promptType = requireEnum(node, "promptType", ALLOWED_PROMPT_TYPES);
        String topic = optionalText(node, "topic");
        String genre = optionalText(node, "genre");
        String wordRange = optionalText(node, "wordRange");
        String requirements = optionalText(node, "requirements");
        String message = optionalText(node, "message");
        Boolean isCompleteOriginalPrompt = optionalBoolean(node, "isCompleteOriginalPrompt");
        Boolean shouldPreserveOriginalWording = optionalBoolean(node, "shouldPreserveOriginalWording");
        Boolean isExamStyleCompatible = optionalBoolean(node, "isExamStyleCompatible");
        List<String> styleCompatibilityReasons = optionalStringArray(node, "styleCompatibilityReasons");
        List<String> missingFields = optionalStringArray(node, "missingFields");
        Boolean requiresUserConfirmation = optionalBoolean(node, "requiresUserConfirmation");
        String confirmationQuestion = optionalText(node, "confirmationQuestion");
        String targetStyle = optionalEnum(node, "targetStyle", ALLOWED_TARGET_STYLES);
        Boolean needsMoreInfo = optionalBoolean(node, "needsMoreInfo");
        String assistantReply = optionalText(node, "assistantReply");
        Boolean promptReady = optionalBoolean(node, "promptReady");
        String readyReason = optionalText(node, "readyReason");
        String nextAction = optionalEnum(node, "nextAction", ALLOWED_NEXT_ACTIONS);
        Boolean needsAttachment = optionalBoolean(node, "needsAttachment");
        String attachmentType = optionalEnum(node, "attachmentType", ALLOWED_ATTACHMENT_TYPES);
        String attachmentSource = optionalEnum(node, "attachmentSource", ALLOWED_ATTACHMENT_SOURCES);
        Boolean attachmentReady = optionalBoolean(node, "attachmentReady");
        String attachmentTitle = optionalText(node, "attachmentTitle");
        String attachmentInstruction = optionalText(node, "attachmentInstruction");
        Map<String, Object> attachmentPayload = optionalObject(node, "attachmentPayload");

        if (genre != null && !ALLOWED_GENRES.contains(genre)) {
            throw new IllegalArgumentException("invalid genre: " + genre);
        }
        if (wordRange != null && !WORD_RANGE_PATTERN.matcher(wordRange).matches()) {
            throw new IllegalArgumentException("invalid wordRange: " + wordRange);
        }
        if ("complete".equals(status) && topic == null) {
            throw new IllegalArgumentException("topic is required when status=complete");
        }
        if ("need_more_info".equals(status) && message == null) {
            throw new IllegalArgumentException("message is required when status=need_more_info");
        }
        if ("invalid".equals(status) && message == null) {
            throw new IllegalArgumentException("message is required when status=invalid");
        }
        if (Boolean.TRUE.equals(requiresUserConfirmation) && confirmationQuestion == null) {
            throw new IllegalArgumentException("confirmationQuestion is required when requiresUserConfirmation=true");
        }
        if (Boolean.FALSE.equals(requiresUserConfirmation) && confirmationQuestion != null) {
            throw new IllegalArgumentException("confirmationQuestion must be null when requiresUserConfirmation=false");
        }
        if (Boolean.TRUE.equals(needsAttachment) && attachmentType == null) {
            throw new IllegalArgumentException("attachmentType is required when needsAttachment=true");
        }
        if (Boolean.FALSE.equals(needsAttachment) && attachmentType != null && !"none".equals(attachmentType)) {
            throw new IllegalArgumentException("attachmentType must be none or null when needsAttachment=false");
        }
        if (Boolean.FALSE.equals(promptReady) && nextAction == null) {
            throw new IllegalArgumentException("nextAction is required when promptReady=false");
        }

        return new ParsedAuditPayload(
                status,
                topic,
                promptType,
                genre,
                wordRange,
                requirements,
                message,
                isCompleteOriginalPrompt,
                shouldPreserveOriginalWording,
                isExamStyleCompatible,
                styleCompatibilityReasons,
                missingFields,
                requiresUserConfirmation,
                confirmationQuestion,
                targetStyle,
                needsMoreInfo,
                assistantReply,
                promptReady,
                readyReason,
                nextAction,
                needsAttachment,
                attachmentType,
                attachmentSource,
                attachmentReady,
                attachmentTitle,
                attachmentInstruction,
                attachmentPayload
        );
    }

    private String requireEnum(JsonNode node, String fieldName, Set<String> allowedValues) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || !field.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        String value = nullIfEmpty(field.asText());
        if (value == null || !allowedValues.contains(value)) {
            throw new IllegalArgumentException("invalid " + fieldName + ": " + value);
        }
        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (!field.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be a string or null");
        }
        return nullIfEmpty(field.asText());
    }

    private String optionalEnum(JsonNode node, String fieldName, Set<String> allowedValues) {
        String value = optionalText(node, fieldName);
        if (value == null) {
            return null;
        }
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException("invalid " + fieldName + ": " + value);
        }
        return value;
    }

    private Boolean optionalBoolean(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (!field.isBoolean()) {
            throw new IllegalArgumentException(fieldName + " must be a boolean or null");
        }
        return field.asBoolean();
    }

    private List<String> optionalStringArray(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (!field.isArray()) {
            throw new IllegalArgumentException(fieldName + " must be an array of strings or null");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : field) {
            if (!item.isTextual()) {
                throw new IllegalArgumentException(fieldName + " must contain only strings");
            }
            String value = nullIfEmpty(item.asText());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, Object> optionalObject(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        if (!field.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be an object or null");
        }
        return objectMapper.convertValue(field, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    // ── 图片识别 ──

    private static final String IMAGE_RECOGNIZE_SYSTEM_PROMPT = """
            你是一个 OCR 助手。请识别图片中的英语作文题目文字，完整输出所有文字内容。
            规则：
            - 只输出图片中的文字内容，不要添加任何解释或分析
            - 保持原文的格式（换行、编号等）
            - 如果图片中没有可识别的文字，输出空字符串
            - 如果图片中包含中文和英文，都要输出
            """;

    public RecognizeTopicImageResponse recognizeImage(String imageBase64) {
        return recognizeImage(imageBase64, null);
    }

    public RecognizeTopicImageResponse recognizeImage(String imageBase64, String aiProvider) {
        log.info("[AUDIT-TOPIC] recognizing image, provider={}, base64Len={}", aiProvider, imageBase64.length());

        try {
            String text = openAiClient.callVisionWithProvider(
                    aiProvider,
                    IMAGE_RECOGNIZE_SYSTEM_PROMPT,
                    "请识别这张图片中的作文题目和写作要求文字。",
                    normalizeImageDataUrl(imageBase64, guessMimeType(imageBase64)),
                    "recognize-topic-image"
            );

            if (text != null) {
                text = text.trim();
            }
            log.info("[AUDIT-TOPIC] image recognized, textLen={}", text != null ? text.length() : 0);
            return new RecognizeTopicImageResponse(text != null && !text.isEmpty() ? text : null);
        } catch (Exception e) {
            log.error("[AUDIT-TOPIC] image recognition failed: {}", e.getMessage());
            return new RecognizeTopicImageResponse(null);
        }
    }

    private String normalizeImageDataUrl(String imageBase64, String mimeType) {
        String value = imageBase64 == null ? null : imageBase64.trim();
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.startsWith("data:")) {
            return value;
        }
        return "data:" + mimeType + ";base64," + value;
    }

    private String guessMimeType(String base64) {
        if (base64.startsWith("/9j/")) return "image/jpeg";
        if (base64.startsWith("iVBOR")) return "image/png";
        if (base64.startsWith("UklGR")) return "image/webp";
        return "image/png"; // 默认
    }

    private record ParsedAuditPayload(
            String status,
            String topic,
            String promptType,
            String genre,
            String wordRange,
            String requirements,
            String message,
            Boolean isCompleteOriginalPrompt,
            Boolean shouldPreserveOriginalWording,
            Boolean isExamStyleCompatible,
            List<String> styleCompatibilityReasons,
            List<String> missingFields,
            Boolean requiresUserConfirmation,
            String confirmationQuestion,
            String targetStyle,
            Boolean needsMoreInfo,
            String assistantReply,
            Boolean promptReady,
            String readyReason,
            String nextAction,
            Boolean needsAttachment,
            String attachmentType,
            String attachmentSource,
            Boolean attachmentReady,
            String attachmentTitle,
            String attachmentInstruction,
            Map<String, Object> attachmentPayload
    ) {}
}
