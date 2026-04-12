package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageRequest;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageResponse;
import com.personalenglishai.backend.service.writing.HandwritingRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class HandwritingRecognitionServiceImpl implements HandwritingRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(HandwritingRecognitionServiceImpl.class);
    private static final String SYSTEM_PROMPT_PATH = "prompts/handwriting/recognize-system.md";
    private static final String USER_PROMPT_PATH = "prompts/handwriting/recognize-user.md";

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public HandwritingRecognitionServiceImpl(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.systemPromptTemplate = loadPromptTemplate(SYSTEM_PROMPT_PATH, defaultSystemPrompt());
        this.userPromptTemplate = loadPromptTemplate(USER_PROMPT_PATH, defaultUserPrompt());
    }

    @Override
    public RecognizeHandwritingImageResponse recognize(RecognizeHandwritingImageRequest request) {
        String traceId = "handwriting-recognize-" + UUID.randomUUID().toString().substring(0, 8);
        String imageBase64 = trimToNull(request.getImageBase64());
        String provider = trimToNull(request.getAiProvider());
        log.info("[WRITING-HANDWRITING] traceId={} provider={} imageBase64Len={}",
                traceId, provider, imageBase64 == null ? 0 : imageBase64.length());

        if (imageBase64 == null) {
            return new RecognizeHandwritingImageResponse(null, null, null, null);
        }

        String imageDataUrl = normalizeImageDataUrl(imageBase64);
        String raw = openAiClient.callVisionWithProvider(
                provider,
                systemPromptTemplate,
                buildUserPrompt(),
                imageDataUrl,
                traceId
        );

        return parseResponse(raw, imageBase64);
    }

    private String buildUserPrompt() {
        return userPromptTemplate;
    }

    private String normalizeImageDataUrl(String imageBase64) {
        String value = trimToNull(imageBase64);
        if (value == null) {
            return null;
        }
        if (value.startsWith("data:")) {
            return value;
        }
        return "data:image/png;base64," + value;
    }

    private RecognizeHandwritingImageResponse parseResponse(String raw, String imageBase64) {
        if (raw == null || raw.isBlank()) {
            return new RecognizeHandwritingImageResponse(imageBase64, null, null, null);
        }

        String stripped = stripCodeFences(raw).trim();
        try {
            JsonNode node = objectMapper.readTree(stripped);
            String recognizedText = firstNonBlank(node.path("recognizedText").asText(null), node.path("text").asText(null));
            String normalizedText = firstNonBlank(node.path("normalizedText").asText(null), recognizedText);
            BigDecimal confidence = null;
            JsonNode confidenceNode = node.path("confidence");
            if (!confidenceNode.isMissingNode() && !confidenceNode.isNull()) {
                try {
                    confidence = confidenceNode.decimalValue();
                } catch (Exception ignored) {
                    confidence = null;
                }
            }
            return new RecognizeHandwritingImageResponse(imageBase64, trimToNull(recognizedText), trimToNull(normalizedText), confidence);
        } catch (Exception e) {
            log.warn("[WRITING-HANDWRITING] parse failed raw={}", raw, e);
            return new RecognizeHandwritingImageResponse(imageBase64, null, null, null);
        }
    }

    private String stripCodeFences(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstBreak >= 0 && lastFence > firstBreak) {
                return trimmed.substring(firstBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String firstNonBlank(String first, String second) {
        String primary = trimToNull(first);
        if (primary != null) {
            return primary;
        }
        return trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String loadPromptTemplate(String path, String fallback) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!content.isEmpty()) {
                return content;
            }
        } catch (Exception e) {
            log.warn("[WRITING-HANDWRITING] failed to load prompt template path={} reason={}", path, e.getMessage());
        }
        return fallback;
    }

    private String defaultSystemPrompt() {
        return """
                你是手写英文作文识别助手。
                任务：
                - 只识别图片中的手写英文作文正文
                - 不要补写缺失内容
                - 不要输出解释、分析、markdown 或代码块
                - 如果无法识别，返回空字符串
                输出要求：
                - 只输出 JSON
                - 结构必须是 {"recognizedText":"...","normalizedText":"...","confidence":0.0}
                - recognizedText：尽量贴近图片原貌的识别结果，按图片中的实际行序保留换行
                - normalizedText：适合直接导入写作区的正文版本
                - 同一自然段内，如果只是因为纸张宽度或手写排版导致的换行，合并为空格
                - 只有在明确出现新段落时，才保留段落分隔，段落之间使用空行分隔
                - 段落分隔优先依据明显空行、明显缩进、称呼与正文切换、结尾祝语单独成段等线索判断
                - 不要把整篇作文压成一个段落
                - 不要润色、扩写、翻译或修正语法，只做识别和段落重建
                """;
    }

    private String defaultUserPrompt() {
        return """
                请识别这张图片中的手写英文作文正文。
                只输出 JSON，不要解释、分析或 markdown。
                请特别注意：图片中的“换行”不一定等于“换段”。
                请在 recognizedText 中保留逐行换行，
                但在 normalizedText 中重建自然段：
                同段内断行改为空格，真正新段落才保留为空行。
                不要补写缺失内容，不要润色改写。
                """;
    }
}
