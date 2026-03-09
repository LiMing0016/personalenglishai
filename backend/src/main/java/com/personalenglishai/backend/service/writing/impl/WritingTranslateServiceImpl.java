package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.TranslateRequest;
import com.personalenglishai.backend.dto.writing.TranslateResponse;
import com.personalenglishai.backend.service.writing.WritingTranslateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WritingTranslateServiceImpl implements WritingTranslateService {

    private static final Logger log = LoggerFactory.getLogger(WritingTranslateServiceImpl.class);
    private static final String TRANSLATE_MODEL = "gpt-4o-mini";

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public WritingTranslateServiceImpl(OpenAiClient openAiClient,
                                        ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public TranslateResponse translate(TranslateRequest request) {
        String mode = normalizeMode(request.getMode());
        Long userId = request.getUserId();
        String traceId = "translate-" + UUID.randomUUID().toString().substring(0, 8);

        // 统一调用逐句翻译，一次 GPT 请求获取所有数据
        String systemPrompt = buildSystemPrompt();
        String userPrompt = "Essay:\n\n" + request.getText().trim();

        log.info("[TRANSLATE] traceId={} userId={} mode={} textLen={}",
                traceId, userId, mode, request.getText().length());

        long start = System.currentTimeMillis();
        String rawResponse = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId,
                TRANSLATE_MODEL, 0.3, 8192);
        long elapsed = System.currentTimeMillis() - start;

        List<TranslateResponse.SentenceTranslation> sentences = parseSentences(rawResponse, traceId);
        log.info("[TRANSLATE] traceId={} mode={} sentences={} elapsed={}ms",
                traceId, mode, sentences.size(), elapsed);

        if ("full".equals(mode)) {
            // 全文模式：按原文段落结构拼接 chinese 字段
            String translation = joinByParagraph(sentences, request.getText().trim());
            TranslateResponse response = new TranslateResponse(translation.isBlank() ? "翻译失败，请重试" : translation);
            response.setSentences(sentences);
            return response;
        }

        // 逐句精讲模式：直接返回句子列表
        return new TranslateResponse(sentences);
    }

    // ── helpers ──

    /** 按原文段落结构拼接翻译 */
    private String joinByParagraph(List<TranslateResponse.SentenceTranslation> sentences, String originalText) {
        if (sentences.isEmpty()) return "";
        String[] paragraphs = originalText.split("\\n\\s*\\n");
        if (paragraphs.length <= 1) {
            return sentences.stream()
                    .map(TranslateResponse.SentenceTranslation::getChinese)
                    .collect(Collectors.joining(""));
        }
        List<String> result = new ArrayList<>();
        int cursor = 0;
        for (String para : paragraphs) {
            StringBuilder paraChinese = new StringBuilder();
            while (cursor < sentences.size()) {
                if (para.contains(sentences.get(cursor).getEnglish())) {
                    paraChinese.append(sentences.get(cursor).getChinese());
                    cursor++;
                } else {
                    break;
                }
            }
            if (paraChinese.length() > 0) {
                result.add(paraChinese.toString());
            }
        }
        while (cursor < sentences.size()) {
            result.add(sentences.get(cursor).getChinese());
            cursor++;
        }
        return String.join("\n\n", result);
    }

    private String normalizeMode(String mode) {
        if (mode == null) return "full";
        String m = mode.trim().toLowerCase();
        return "detailed".equals(m) ? "detailed" : "full";
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

    private String buildSystemPrompt() {
        return """
                你是一位经验丰富的高中英语老师，正在给学生逐句讲解他们写的英文作文。
                你的讲解风格亲切自然，像课堂上面对面辅导一样，会告诉学生哪些表达很地道、考试能加分，也会指出可以改进的地方。

                对每一句话，你需要输出：
                1. chinese：准确、自然的中文翻译
                2. structure：句子结构拆解（用中文讲解，像老师在黑板上分析句子成分一样：主语是什么、谓语是什么、用了什么从句/句型/修辞手法，80字以内）
                3. highlights：该句中值得学习的高级词汇、词组或搭配（数组，每个元素包含 word/meaning/detail 三个字段）
                   - word：原文中的词或词组
                   - meaning：中文释义（简短）
                   - detail：像老师一样讲解用法，包括常见搭配、同义替换、写作中的使用技巧、能否帮助提分等（60字以内）
                   - 每句提取 1~4 个，优先挑高级词汇和地道表达
                   - 如果整句都很基础没什么亮点，highlights 可以为空数组

                其他规则：
                - 将作文按句子拆分（以 . ! ? 结尾为一句）
                - english 必须是作文中的原文精确子串
                - 翻译必须准确，不要添加原文没有的内容

                只输出合法 JSON：
                {"sentences":[{"english":"原句","chinese":"翻译","structure":"句子结构分析","highlights":[{"word":"词","meaning":"释义","detail":"用法讲解"}]}]}
                """;
    }

    private List<TranslateResponse.SentenceTranslation> parseSentences(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[TRANSLATE] traceId={} empty response", traceId);
            return List.of();
        }

        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            JsonNode arr = node.path("sentences");
            if (!arr.isArray()) {
                log.warn("[TRANSLATE] traceId={} no sentences array", traceId);
                return List.of();
            }

            List<TranslateResponse.SentenceTranslation> items = new ArrayList<>();
            for (JsonNode s : arr) {
                String english = s.path("english").asText(null);
                String chinese = s.path("chinese").asText(null);
                if (english == null || english.isBlank() || chinese == null || chinese.isBlank()) continue;

                TranslateResponse.SentenceTranslation item = new TranslateResponse.SentenceTranslation();
                item.setEnglish(english.trim());
                item.setChinese(chinese.trim());

                String structure = s.path("structure").asText(null);
                if (structure != null && !"null".equals(structure) && !structure.isBlank()) {
                    item.setStructure(structure.trim());
                }

                JsonNode hlArr = s.path("highlights");
                if (hlArr.isArray() && !hlArr.isEmpty()) {
                    List<TranslateResponse.HighlightItem> highlights = new ArrayList<>();
                    for (JsonNode h : hlArr) {
                        String word = h.path("word").asText(null);
                        String meaning = h.path("meaning").asText(null);
                        String detail = h.path("detail").asText(null);
                        if (word != null && !word.isBlank()) {
                            highlights.add(new TranslateResponse.HighlightItem(
                                    word.trim(),
                                    meaning != null ? meaning.trim() : null,
                                    detail != null ? detail.trim() : null));
                        }
                    }
                    if (!highlights.isEmpty()) {
                        item.setHighlights(highlights);
                    }
                }

                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("[TRANSLATE] traceId={} parse failed: {}", traceId, raw, e);
            return List.of();
        }
    }
}
