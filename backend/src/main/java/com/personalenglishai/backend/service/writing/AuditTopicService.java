package com.personalenglishai.backend.service.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.QwenService;
import com.personalenglishai.backend.dto.writing.AuditTopicRequest;
import com.personalenglishai.backend.dto.writing.AuditTopicResponse;
import com.personalenglishai.backend.dto.writing.RecognizeTopicImageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditTopicService {

    private static final Logger log = LoggerFactory.getLogger(AuditTopicService.class);
    private final QwenService qwenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是考试写作助手。分析用户输入的英语作文题目，提取并补全以下信息：
            1. topic：题目正文。尽量保留用户原始题目文本，不要概括成“根据所给图表写一篇作文”这种过于泛化的摘要；若原文包含英文题目要求，优先保留原英文正文
            2. genre：体裁（书信、议论文、说明文、演讲稿、看图作文、通知、日记，或 null）
            3. wordRange：字数范围（如 "80-120"，或 null）
            4. requirements：写作要求/要点（如 "1) describe the picture briefly 2) interpret the meaning 3) give your comments"，从原文提取，没有则 null）
            5. status：
               - "complete"：题目有效且信息完整
               - "need_more_info"：题目有效但缺少体裁或字数，在 message 中友好提示
               - "invalid"：输入明显不是作文题目（数字、乱码等），在 message 中引导用户
            6. message：给用户的中文提示（status 为 complete 时可为 null）

            规则：
            - 如果用户已选择体裁或字数（genre/wordRange 不为空），直接采用，不要覆盖
            - 如果用户未选择，尝试从题目文本中推断
            - topic 必须尽量贴近原题，不要重写成简短标题，不要丢失图表/图片/材料等关键信息
            - requirements 是题目中的具体写作要点（如需要描述图片、阐释含义、给出评论等），原样提取，不要编造
            - 推断不出的字段输出 null，不要编造
            - 用中文回复 message，语气友好简洁
            - genre 必须是以下之一：书信、议论文、说明文、演讲稿、看图作文、通知、日记，或 null

            只输出合法 JSON，不要输出其他内容：
            {"status":"...","topic":"...","genre":"...","wordRange":"...","requirements":"...","message":"..."}
            """;

    public AuditTopicService(QwenService qwenService) {
        this.qwenService = qwenService;
    }

    public AuditTopicResponse audit(AuditTopicRequest request) {
        if (!qwenService.isEnabled()) {
            log.warn("[AUDIT-TOPIC] Qwen not configured, falling back to pass-through");
            return fallback(request);
        }

        String userPrompt = buildUserPrompt(request);
        log.info("[AUDIT-TOPIC] auditing topic, len={}", request.getTopic().length());

        try {
            String raw = qwenService.chat(SYSTEM_PROMPT, userPrompt);
            return parseResponse(raw, request);
        } catch (Exception e) {
            log.error("[AUDIT-TOPIC] Qwen call failed, falling back: {}", e.getMessage());
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
        return sb.toString();
    }

    private AuditTopicResponse parseResponse(String raw, AuditTopicRequest request) {
        if (raw == null || raw.isBlank()) {
            return fallback(request);
        }

        try {
            String cleaned = stripCodeFences(raw);
            JsonNode node = objectMapper.readTree(cleaned);

            String status = node.path("status").asText("complete");
            String topic = normalizeTopic(node.path("topic").asText(request.getTopic()), request.getTopic());
            String genre = nullIfEmpty(node.path("genre").asText(null));
            String wordRange = nullIfEmpty(node.path("wordRange").asText(null));
            String requirements = nullIfEmpty(node.path("requirements").asText(null));
            String message = nullIfEmpty(node.path("message").asText(null));

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
                return AuditTopicResponse.needMoreInfo(topic, genre, wordRange, requirements, message);
            }

            return AuditTopicResponse.complete(topic, genre, wordRange, requirements);
        } catch (Exception e) {
            log.warn("[AUDIT-TOPIC] parse failed: {}", raw, e);
            return fallback(request);
        }
    }

    /** 千问不可用时的兜底：直接透传用户输入 */
    private AuditTopicResponse fallback(AuditTopicRequest request) {
        return AuditTopicResponse.complete(
                request.getTopic(),
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
        if (!qwenService.isEnabled()) {
            log.warn("[AUDIT-TOPIC] Qwen not configured, cannot recognize image");
            return new RecognizeTopicImageResponse(null);
        }

        log.info("[AUDIT-TOPIC] recognizing image, base64Len={}", imageBase64.length());

        try {
            // 推断 MIME 类型
            String mimeType = guessMimeType(imageBase64);
            String text = qwenService.visionChat(
                    IMAGE_RECOGNIZE_SYSTEM_PROMPT,
                    "请识别这张图片中的作文题目和写作要求文字。",
                    imageBase64,
                    mimeType
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

    private String guessMimeType(String base64) {
        if (base64.startsWith("/9j/")) return "image/jpeg";
        if (base64.startsWith("iVBOR")) return "image/png";
        if (base64.startsWith("UklGR")) return "image/webp";
        return "image/png"; // 默认
    }
}
