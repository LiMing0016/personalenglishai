package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.dto.writing.WritingMaterialRequest;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse;
import com.personalenglishai.backend.dto.writing.WritingMaterialResponse.*;
import com.personalenglishai.backend.service.writing.WritingMaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WritingMaterialServiceImpl implements WritingMaterialService {

    private static final Logger log = LoggerFactory.getLogger(WritingMaterialServiceImpl.class);
    private static final String MATERIAL_MODEL = "gpt-4o-mini";

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public WritingMaterialServiceImpl(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public WritingMaterialResponse generate(WritingMaterialRequest request) {
        String traceId = "material-" + UUID.randomUUID().toString().substring(0, 8);
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request);

        log.info("[WRITING-MATERIAL] traceId={} userId={} stage={} mode={} topicLen={}",
                traceId,
                request.getUserId(),
                normalize(request.getStudyStage()),
                normalize(request.getWritingMode()),
                request.getTaskPrompt() == null ? 0 : request.getTaskPrompt().length());

        long start = System.currentTimeMillis();
        String raw = openAiClient.callWithTraceId(systemPrompt, userPrompt, traceId,
                MATERIAL_MODEL, 0.5, 4096);
        long elapsed = System.currentTimeMillis() - start;

        WritingMaterialResponse response = parseResponse(raw, traceId);
        log.info("[WRITING-MATERIAL] traceId={} vocabGroups={} phrases={} sentences={} elapsed={}ms",
                traceId,
                sizeOf(response.getVocabulary()),
                sizeOf(response.getPhrases()),
                sizeOf(response.getSentences()),
                elapsed);
        return response;
    }

    private String buildSystemPrompt() {
        return """
                你是一位资深英语写作教练，专门为英语学习者整理高质量写作素材。

                你的任务是围绕指定作文主题，生成适合该学段英语考试写作的写作素材。

                素材需要分为三类：

                1. 主题词（Vocabulary）
                2. 常用短语（Phrases）
                3. 常用句子（Sentences）

                生成要求如下：

                【主题词 Vocabulary】
                - 词汇必须紧密围绕文章主题，而不是泛学术词汇
                - 词汇应适合考试写作
                - 每个词汇提供中文解释
                - 请将主题词按语义进行分类，例如：
                  1. 核心主题词（Topic Core）— 提供 12~16 个，必须是偶数
                  2. 正面影响（Advantages / Positive Effects）— 提供 8~14 个，必须是偶数
                  3. 负面影响（Disadvantages / Concerns）— 提供 8~14 个，必须是偶数
                - 分类名称可根据主题灵活调整，但至少包含上述 3 类

                【常用短语 Phrases】
                - 提供 8~12 个高频写作短语
                - 短语应自然、常见、适用于议论文写作
                - 每个短语提供中文解释（不超过15字）
                - 不需要提供示例句

                【常用句子 Sentences】
                - 提供 6~8 个完整的高质量写作句子
                - 句子必须是完整句，不要使用占位符
                - 句子应自然、正式、适合英语考试写作
                - 尽量覆盖不同写作功能，例如：
                  - 表达观点
                  - 解释原因
                  - 举例说明
                  - 表达影响
                  - 提出建议
                  - 总结观点
                - 句子应具有较高复用性
                - 每个句子提供简要中文解释
                - 句子水平要符合当前学段

                根据学段调整输出难度：
                - 初中：词汇基础，短语简单实用，句子结构简洁
                - 高中：词汇偏高考高分表达，短语地道，句子有一定复杂度
                - 大学/考研：词汇学术化，短语高级，句子结构多样且有深度

                如未提供学段，默认按高中水平输出。

                输出要求：
                必须严格按照以下 JSON 结构输出：

                {
                  "topic": "",
                  "stage": "",
                  "vocabulary": [],
                  "phrases": [],
                  "sentences": []
                }

                字段结构说明：
                vocabulary 数组元素：{ "category": "分类名称", "words": [{ "word": "", "meaning": "" }] }
                phrases 数组元素：{ "phrase": "", "meaning": "" }
                sentences 数组元素：{ "sentence": "", "description": "" }

                严格要求：
                - 单词、短语、句子必须是英文
                - 所有解释必须是中文
                - 不要输出解释说明
                - 只输出合法 JSON
                """;
    }

    private String buildUserPrompt(WritingMaterialRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Topic: ").append(request.getTaskPrompt().trim()).append('\n');
        sb.append("Stage: ").append(normalize(request.getStudyStage()));
        return sb.toString();
    }

    // ── Response Parsing ──

    private WritingMaterialResponse parseResponse(String raw, String traceId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[WRITING-MATERIAL] traceId={} empty response", traceId);
            return emptyResponse();
        }
        try {
            JsonNode node = objectMapper.readTree(stripCodeFences(raw));
            WritingMaterialResponse response = new WritingMaterialResponse();
            response.setTopic(nullIfBlank(node.path("topic").asText(null)));
            response.setStage(nullIfBlank(node.path("stage").asText(null)));
            response.setVocabulary(readVocabulary(node.path("vocabulary")));
            response.setPhrases(readPhrases(node.path("phrases")));
            response.setSentences(readSentences(node.path("sentences")));
            return response;
        } catch (Exception e) {
            log.warn("[WRITING-MATERIAL] traceId={} parse failed raw={}", traceId, raw, e);
            return emptyResponse();
        }
    }

    private List<VocabularyGroup> readVocabulary(JsonNode node) {
        List<VocabularyGroup> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode groupNode : node) {
            VocabularyGroup group = new VocabularyGroup();
            group.setCategory(nullIfBlank(groupNode.path("category").asText(null)));
            List<VocabularyItem> words = new ArrayList<>();
            JsonNode wordsNode = groupNode.path("words");
            if (wordsNode != null && wordsNode.isArray()) {
                for (JsonNode w : wordsNode) {
                    VocabularyItem v = new VocabularyItem();
                    v.setWord(nullIfBlank(w.path("word").asText(null)));
                    v.setMeaning(nullIfBlank(w.path("meaning").asText(null)));
                    if (v.getWord() != null) words.add(v);
                }
            }
            group.setWords(words);
            if (group.getCategory() != null && !words.isEmpty()) list.add(group);
        }
        return list;
    }

    private List<PhraseItem> readPhrases(JsonNode node) {
        List<PhraseItem> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode item : node) {
            PhraseItem p = new PhraseItem();
            p.setPhrase(nullIfBlank(item.path("phrase").asText(null)));
            p.setMeaning(nullIfBlank(item.path("meaning").asText(null)));
            if (p.getPhrase() != null) list.add(p);
        }
        return list;
    }

    private List<SentenceItem> readSentences(JsonNode node) {
        List<SentenceItem> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode item : node) {
            SentenceItem s = new SentenceItem();
            s.setSentence(nullIfBlank(item.path("sentence").asText(null)));
            s.setDescription(nullIfBlank(item.path("description").asText(null)));
            if (s.getSentence() != null) list.add(s);
        }
        return list;
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

    private WritingMaterialResponse emptyResponse() {
        WritingMaterialResponse r = new WritingMaterialResponse();
        r.setVocabulary(List.of());
        r.setPhrases(List.of());
        r.setSentences(List.of());
        return r;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }
}
