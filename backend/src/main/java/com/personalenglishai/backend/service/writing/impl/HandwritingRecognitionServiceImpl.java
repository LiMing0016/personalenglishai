package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageRequest;
import com.personalenglishai.backend.dto.writing.RecognizeHandwritingImageResponse;
import com.personalenglishai.backend.service.writing.HandwritingRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class HandwritingRecognitionServiceImpl implements HandwritingRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(HandwritingRecognitionServiceImpl.class);

    private static final String SYSTEM_PROMPT = """
            你是手写英文作文识别助手。
            任务：
            - 只识别图片中的手写英文作文正文
            - 保留原文自然段和换行
            - 不要补写缺失内容
            - 不要输出解释、分析、markdown 或代码块
            - 如果无法识别，返回空字符串
            输出要求：
            - 只输出 JSON
            - 结构必须是 {"recognizedText":"...","normalizedText":"...","confidence":0.0}
            - recognizedText 保留尽可能贴近原文的识别结果
            - normalizedText 返回适合直接粘贴到作文区的正文
            """;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public HandwritingRecognitionServiceImpl(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
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

        String raw = openAiClient.callWithProvider(
                provider,
                SYSTEM_PROMPT,
                buildUserPrompt(imageBase64),
                traceId
        );

        return parseResponse(raw, imageBase64);
    }

    private String buildUserPrompt(String imageBase64) {
        return """
                请识别这张图片中的手写英文作文正文。
                图片内容（base64，可能带 data URL 前缀）：
                %s
                """.formatted(imageBase64);
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
            String text = trimToNull(stripped);
            return new RecognizeHandwritingImageResponse(imageBase64, text, text, null);
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
}
