package com.personalenglishai.backend.service.writing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.client.OpenAiClient;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextRequest;
import com.personalenglishai.backend.ai.client.OpenAiResponsesTextResult;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.writing.WritingDocumentAssetResponse;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.entity.EssayEvaluation;
import com.personalenglishai.backend.entity.WritingMetadata;
import com.personalenglishai.backend.entity.assistant.AssistantConversation;
import com.personalenglishai.backend.entity.assistant.AssistantMessage;
import com.personalenglishai.backend.entity.writing.WritingDocumentAssetSnapshot;
import com.personalenglishai.backend.entity.writing.WritingLearningAssetPreviewItem;
import com.personalenglishai.backend.entity.writing.WritingLearningAssetPreviewRun;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantConversationMapper;
import com.personalenglishai.backend.mapper.assistant.AssistantMessageMapper;
import com.personalenglishai.backend.mapper.writing.WritingDocumentAssetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WritingDocumentAssetService {
    private static final String WORKSPACE_DEFAULT = "default";
    private static final int MAX_EVALUATIONS = 50;
    private static final String LEARNING_ASSET_PROVIDER = "deepseek";
    private static final Set<String> LEARNING_ASSET_TYPES = Set.of(
            "word", "phrase", "sentence", "grammar", "writing_strategy");

    private final DocumentMapper documentMapper;
    private final WritingMetadataMapper writingMetadataMapper;
    private final EssayEvaluationMapper essayEvaluationMapper;
    private final AssistantConversationMapper assistantConversationMapper;
    private final AssistantMessageMapper assistantMessageMapper;
    private final WritingDocumentAssetMapper assetMapper;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String learningAssetModel;

    @Autowired
    public WritingDocumentAssetService(
            DocumentMapper documentMapper,
            WritingMetadataMapper writingMetadataMapper,
            EssayEvaluationMapper essayEvaluationMapper,
            AssistantConversationMapper assistantConversationMapper,
            AssistantMessageMapper assistantMessageMapper,
            WritingDocumentAssetMapper assetMapper,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper,
            @Value("${writing.asset.learning.deepseek.model:deepseek-chat}") String learningAssetModel) {
        this.documentMapper = documentMapper;
        this.writingMetadataMapper = writingMetadataMapper;
        this.essayEvaluationMapper = essayEvaluationMapper;
        this.assistantConversationMapper = assistantConversationMapper;
        this.assistantMessageMapper = assistantMessageMapper;
        this.assetMapper = assetMapper;
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.learningAssetModel = learningAssetModel;
    }

    WritingDocumentAssetService(
            DocumentMapper documentMapper,
            WritingMetadataMapper writingMetadataMapper,
            EssayEvaluationMapper essayEvaluationMapper,
            AssistantConversationMapper assistantConversationMapper,
            AssistantMessageMapper assistantMessageMapper,
            WritingDocumentAssetMapper assetMapper,
            OpenAiClient openAiClient,
            ObjectMapper objectMapper) {
        this(
                documentMapper,
                writingMetadataMapper,
                essayEvaluationMapper,
                assistantConversationMapper,
                assistantMessageMapper,
                assetMapper,
                openAiClient,
                objectMapper,
                "deepseek-chat");
    }

    @Transactional
    public WritingDocumentAssetSnapshot refreshSnapshot(
            String tenantId,
            String workspaceId,
            String publicDocId,
            Long userId) {
        Document doc = ensureOwnedDocument(tenantId, workspaceId, publicDocId, userId);
        AssetSource source = loadAssetSource(doc);
        return writeSnapshot(doc, source);
    }

    private WritingDocumentAssetSnapshot writeSnapshot(Document doc, AssetSource source) {
        LocalDateTime generatedAt = LocalDateTime.now();
        String markdown = buildMarkdown(doc, source, generatedAt);
        String snapshotJson = buildSnapshotJson(doc, source, generatedAt);

        WritingDocumentAssetSnapshot snapshot = new WritingDocumentAssetSnapshot();
        snapshot.setDocumentId(doc.getId());
        snapshot.setUserId(doc.getOwnerUserId());
        snapshot.setSnapshotUid("asset-" + UUID.randomUUID());
        snapshot.setMarkdownContent(markdown);
        snapshot.setSnapshotJson(snapshotJson);
        snapshot.setLatestRevision(doc.getLatestRevision());
        snapshot.setEvaluationCount(source.evaluations().size());
        snapshot.setCoachMessageCount(source.coachMessages().values().stream().mapToInt(List::size).sum());
        snapshot.setGeneratedAt(generatedAt);
        assetMapper.upsertSnapshot(snapshot);
        return snapshot;
    }

    @Transactional
    public void linkCoachConversation(
            String tenantId,
            String workspaceId,
            String publicDocId,
            Long userId,
            String conversationUid) {
        Document doc = ensureOwnedDocument(tenantId, workspaceId, publicDocId, userId);
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid.trim();
        if (normalizedConversationUid.isBlank()) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "conversationId is required");
        }
        AssistantConversation conversation = assistantConversationMapper.findOwnedActiveByUid(userId, normalizedConversationUid);
        if (conversation == null) {
            throw new BizException(ErrorCode.DOC_FORBIDDEN, "conversation is not owned by current user");
        }
        assetMapper.upsertConversationLink(userId, doc.getId(), normalizedConversationUid);
    }

    public WritingDocumentAssetResponse getAsset(String tenantId, String workspaceId, String publicDocId, Long userId) {
        Document doc = ensureOwnedDocument(tenantId, workspaceId, publicDocId, userId);
        AssetSource source = loadAssetSource(doc);
        WritingDocumentAssetSnapshot snapshot = assetMapper.findSnapshot(userId, doc.getId());
        if (snapshot == null) {
            snapshot = writeSnapshot(doc, source);
        }

        WritingDocumentAssetResponse response = new WritingDocumentAssetResponse();
        response.setDocId(doc.getPublicId());
        response.setTitle(doc.getTitle());
        response.setTaskPrompt(doc.getTaskPrompt());
        response.setContent(source.revision() == null ? "" : nullToEmpty(source.revision().getContent()));
        response.setLatestRevision(doc.getLatestRevision());
        response.setLatestScore(doc.getLatestScore());
        response.setSubmitCount(doc.getSubmitCount());
        response.setArchived(doc.getStatus() != null && doc.getStatus() == 2);
        response.setMarkdown(snapshot.getMarkdownContent());
        response.setGeneratedAt(snapshot.getGeneratedAt());
        response.setStale(isStale(doc, snapshot, source));
        response.setEvaluations(source.evaluations().stream().map(this::toEvaluationItem).toList());
        response.setCoachConversations(source.conversations().stream()
                .map(conversation -> toCoachConversationItem(conversation, source.messagesFor(conversation.getConversationUid()).size()))
                .toList());
        response.setLearningAssetPreview(loadLearningAssetPreview(userId, doc.getId()));
        return response;
    }

    @Transactional
    public WritingDocumentAssetResponse refreshLearningAssetPreview(
            String tenantId,
            String workspaceId,
            String publicDocId,
            Long userId) {
        Document doc = ensureOwnedDocument(tenantId, workspaceId, publicDocId, userId);
        AssetSource source = loadAssetSource(doc);
        LocalDateTime generatedAt = LocalDateTime.now();
        String runUid = "wlap-" + UUID.randomUUID();
        WritingLearningAssetPreviewRun run = new WritingLearningAssetPreviewRun();
        run.setRunUid(runUid);
        run.setDocumentId(doc.getId());
        run.setUserId(userId);
        run.setModel(learningAssetModel);
        run.setGeneratedAt(generatedAt);

        List<WritingLearningAssetPreviewItem> items = List.of();
        try {
            OpenAiResponsesTextResult result = openAiClient.createTextResponse(new OpenAiResponsesTextRequest(
                    LEARNING_ASSET_PROVIDER,
                    learningAssetModel,
                    buildLearningAssetInstructions(),
                    buildLearningAssetInput(doc, source),
                    null,
                    "writing-learning-assets:" + userId,
                    "24h",
                    false,
                    2200));
            ParsedLearningAssetPreview parsed = parseLearningAssetPreview(result.outputText(), runUid, doc.getId(), userId);
            items = parsed.items();
            run.setStatus("completed");
            run.setSummary(parsed.summary());
            run.setResultJson(stripJsonFence(result.outputText()));
            run.setInputTokenCount(toLong(result.inputTokens()));
            run.setOutputTokenCount(toLong(result.outputTokens()));
            run.setItemCount(items.size());
        } catch (Exception e) {
            run.setStatus("failed");
            run.setSummary("");
            run.setErrorMessage(truncate(e.getMessage(), 1000));
            run.setItemCount(0);
        }

        assetMapper.insertLearningAssetPreviewRun(run);
        assetMapper.replaceLearningAssetPreviewItems(runUid, items);

        WritingDocumentAssetResponse response = getAsset(tenantId, workspaceId, publicDocId, userId);
        response.setLearningAssetPreview(toLearningAssetPreview(run, items));
        return response;
    }

    public String getMarkdown(String tenantId, String workspaceId, String publicDocId, Long userId) {
        WritingDocumentAssetResponse asset = getAsset(tenantId, workspaceId, publicDocId, userId);
        return asset.getMarkdown();
    }

    public String getCoachConversationMarkdown(
            String tenantId,
            String workspaceId,
            String publicDocId,
            Long userId,
            String conversationUid) {
        Document doc = ensureOwnedDocument(tenantId, workspaceId, publicDocId, userId);
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid.trim();
        if (normalizedConversationUid.isBlank()) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "conversationId is required");
        }

        AssetSource source = loadAssetSource(doc);
        AssistantConversation conversation = source.conversations().stream()
                .filter(item -> normalizedConversationUid.equals(item.getConversationUid()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.DOC_FORBIDDEN, "conversation is not linked to this document"));
        return buildCoachConversationMarkdown(doc, conversation, source.messagesFor(normalizedConversationUid));
    }

    private Document ensureOwnedDocument(String tenantId, String workspaceId, String publicDocId, Long userId) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(
                publicDocId,
                tenantId,
                workspaceId != null && !workspaceId.isBlank() ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND, "document not found");
        }
        if (!doc.getOwnerUserId().equals(userId)) {
            throw new BizException(ErrorCode.DOC_FORBIDDEN, "not owner");
        }
        return doc;
    }

    private AssetSource loadAssetSource(Document doc) {
        DocumentRevision revision = documentMapper.findRevisionByDocumentIdAndRevision(doc.getId(), doc.getLatestRevision());
        WritingMetadata metadata = writingMetadataMapper.selectByDocumentId(doc.getId());
        List<EssayEvaluation> evaluations = essayEvaluationMapper.selectByDocumentId(doc.getId(), 0, MAX_EVALUATIONS);
        long evaluationTotal = essayEvaluationMapper.countByDocumentId(doc.getId());
        List<AssistantConversation> conversations = assetMapper.selectLinkedConversations(doc.getOwnerUserId(), doc.getId());
        conversations = recoverHistoricalCoachConversations(doc, metadata, conversations);
        Map<String, List<AssistantMessage>> messages = new LinkedHashMap<>();
        for (AssistantConversation conversation : conversations) {
            messages.put(conversation.getConversationUid(), assistantMessageMapper.selectByConversationUid(conversation.getConversationUid()));
        }
        return new AssetSource(revision, metadata, evaluations, evaluationTotal, conversations, messages);
    }

    private List<AssistantConversation> recoverHistoricalCoachConversations(
            Document doc,
            WritingMetadata metadata,
            List<AssistantConversation> linkedConversations) {
        List<String> probes = buildCoachConversationRecoveryProbes(doc, metadata);
        if (probes.isEmpty()) {
            return linkedConversations;
        }

        List<AssistantConversation> recovered = assetMapper.selectRecoverableCoachConversations(doc.getOwnerUserId(), probes);
        if (recovered.isEmpty()) {
            return linkedConversations;
        }

        Map<String, AssistantConversation> merged = new LinkedHashMap<>();
        for (AssistantConversation conversation : linkedConversations) {
            merged.put(conversation.getConversationUid(), conversation);
        }
        for (AssistantConversation conversation : recovered) {
            String conversationUid = conversation.getConversationUid();
            if (conversationUid == null || conversationUid.isBlank()) {
                continue;
            }
            if (!merged.containsKey(conversationUid)) {
                assetMapper.upsertConversationLink(doc.getOwnerUserId(), doc.getId(), conversationUid);
            }
            merged.putIfAbsent(conversationUid, conversation);
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> buildCoachConversationRecoveryProbes(Document doc, WritingMetadata metadata) {
        Set<String> probes = new LinkedHashSet<>();
        addProbe(probes, doc.getTitle());
        addProbe(probes, doc.getTaskPrompt());
        if (metadata != null) {
            addProbe(probes, metadata.getTopicTitle());
            addProbe(probes, metadata.getPromptText());
        }
        return new ArrayList<>(probes);
    }

    private void addProbe(Set<String> probes, String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 6) {
            return;
        }
        probes.add(normalized.length() > 160 ? normalized.substring(0, 160) : normalized);
    }

    private String buildMarkdown(Document doc, AssetSource source, LocalDateTime generatedAt) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(defaultText(doc.getTitle(), "未命名作文")).append("\n\n");
        markdown.append("## 作文概览\n\n");
        markdown.append("- 作文 ID：`").append(doc.getPublicId()).append("`\n");
        markdown.append("- 最新版本：").append(doc.getLatestRevision()).append("\n");
        markdown.append("- 最新分数：").append(doc.getLatestScore() == null ? "未评分" : doc.getLatestScore()).append("\n");
        markdown.append("- 提交次数：").append(doc.getSubmitCount() == null ? 0 : doc.getSubmitCount()).append("\n");
        markdown.append("- 归档状态：").append(doc.getStatus() != null && doc.getStatus() == 2 ? "已归档" : "未归档").append("\n\n");

        markdown.append("## 题目信息\n\n");
        WritingMetadata metadata = source.metadata();
        markdown.append("- 写作模式：").append(defaultText(metadata == null ? null : metadata.getMode(), doc.getTaskPrompt() == null ? "free" : "exam")).append("\n");
        markdown.append("- 学段：").append(defaultText(metadata == null ? null : metadata.getStudyStage(), "未记录")).append("\n");
        markdown.append("- 题目：").append(defaultText(doc.getTaskPrompt(), metadata == null ? null : metadata.getPromptText(), "自由写作，无固定题目")).append("\n\n");

        markdown.append("## 作文正文\n\n");
        markdown.append(nullToEmpty(source.revision() == null ? null : source.revision().getContent()).trim()).append("\n\n");

        markdown.append("## 评分记录\n\n");
        if (source.evaluations().isEmpty()) {
            markdown.append("暂无评分记录。\n\n");
        } else {
            for (EssayEvaluation evaluation : source.evaluations()) {
                markdown.append("### 评分 #").append(evaluation.getId()).append("\n\n");
                markdown.append("- 总分：").append(evaluation.getOverallScore() == null ? "未记录" : evaluation.getOverallScore()).append("\n");
                markdown.append("- 等级：").append(defaultText(evaluation.getBand(), "未记录")).append("\n");
                markdown.append("- 结构：").append(defaultText(evaluation.getStructureScore(), "未记录")).append("\n");
                markdown.append("- 词汇：").append(defaultText(evaluation.getVocabularyScore(), "未记录")).append("\n");
                markdown.append("- 语法：").append(defaultText(evaluation.getGrammarScore(), "未记录")).append("\n");
                markdown.append("- 错误数：").append(defaultText(evaluation.getTotalErrorCount(), "未记录")).append("\n");
                markdown.append("- 评估时间：").append(defaultText(evaluation.getCreatedAt(), "未记录")).append("\n\n");
            }
        }

        markdown.append("## 写作教练对话\n\n");
        if (source.conversations().isEmpty()) {
            markdown.append("暂无写作教练对话。\n\n");
        } else {
            for (AssistantConversation conversation : source.conversations()) {
                markdown.append("### ").append(defaultText(conversation.getTitle(), conversation.getConversationUid())).append("\n\n");
                for (AssistantMessage message : source.messagesFor(conversation.getConversationUid())) {
                    markdown.append("#### ").append(roleLabel(message.getRole())).append("\n\n");
                    markdown.append(cleanCoachMessageForMarkdown(message.getContent())).append("\n\n");
                }
            }
        }

        markdown.append("## 档案元信息\n\n");
        markdown.append("- 生成时间：").append(generatedAt).append("\n");
        markdown.append("- 评分记录数：").append(source.evaluationTotal()).append("\n");
        markdown.append("- 教练会话数：").append(source.conversations().size()).append("\n");
        return markdown.toString();
    }

    private String buildSnapshotJson(Document doc, AssetSource source, LocalDateTime generatedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("generatedAt", generatedAt.toString());
        snapshot.put("document", Map.of(
                "id", doc.getPublicId(),
                "title", nullToEmpty(doc.getTitle()),
                "taskPrompt", nullToEmpty(doc.getTaskPrompt()),
                "latestRevision", doc.getLatestRevision() == null ? 0 : doc.getLatestRevision(),
                "latestScore", doc.getLatestScore() == null ? "" : doc.getLatestScore(),
                "submitCount", doc.getSubmitCount() == null ? 0 : doc.getSubmitCount(),
                "status", doc.getStatus() == null ? 0 : doc.getStatus()));
        snapshot.put("content", nullToEmpty(source.revision() == null ? null : source.revision().getContent()));
        snapshot.put("metadata", toMetadataSnapshot(source.metadata()));
        snapshot.put("evaluations", source.evaluations().stream().map(this::toEvaluationSnapshot).toList());
        snapshot.put("coachConversations", source.conversations().stream().map(conversation -> Map.of(
                "id", conversation.getConversationUid(),
                "title", nullToEmpty(conversation.getTitle()),
                "messages", source.messagesFor(conversation.getConversationUid()).stream().map(this::toMessageSnapshot).toList()
        )).toList());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.COMMON_SYSTEM_ERROR, "生成作文资产快照失败");
        }
    }

    private String buildLearningAssetInstructions() {
        return """
                你是英语写作学习资产提取 Agent。请从一篇作文、评分和写作教练对话中提取最值得用户复盘的学习资产。
                输出严格 JSON，不要 Markdown，不要解释 JSON 外内容。
                格式：
                {"summary":"一句话总结用户本次最值得复盘的方向","items":[{"assetType":"word|phrase|sentence|grammar|writing_strategy","sourceType":"user_focus|coach_feedback|system_discovered","displayText":"展示文本","originalText":"用户原文或原问题中的表达","recommendedText":"推荐表达或修正版","meaningZh":"中文含义","explanation":"为什么这样更好","valueReasonForUser":"为什么这条对这个用户有价值，必须结合用户原文或提问","howToReuse":"下次作文如何复用","reviewPrompt":"给用户复习时的一句话提示","sourceQuestion":"用户在教练对话中的相关问题，没有则为空","sourceExcerpt":"证据片段","confidence":0.0,"learningValueScore":0}]}
                规则：
                - 优先提取用户问过、写错过、教练重点讲过的内容。
                - 只保留用户能明显感到有学习价值的内容，不要抽普通功能词或空泛建议。
                - sentence 必须是完整英文句子；phrase 是 2 到 8 个词；grammar 聚焦一个可复用语法点。
                - valueReasonForUser 必须具体指出这条和用户作文/对话的关系。
                - 最多返回 12 条。
                """;
    }

    private String buildLearningAssetInput(Document doc, AssetSource source) {
        StringBuilder input = new StringBuilder();
        input.append("作文标题：").append(defaultText(doc.getTitle(), "未命名作文")).append("\n");
        input.append("作文题目：").append(defaultText(doc.getTaskPrompt(), "无固定题目")).append("\n\n");
        input.append("作文正文：\n").append(truncate(nullToEmpty(source.revision() == null ? null : source.revision().getContent()), 6000)).append("\n\n");
        input.append("评分摘要：\n");
        for (EssayEvaluation evaluation : source.evaluations()) {
            input.append("- 总分 ").append(defaultText(evaluation.getOverallScore(), "未记录"))
                    .append("，等级 ").append(defaultText(evaluation.getBand(), "未记录"))
                    .append("，结构 ").append(defaultText(evaluation.getStructureScore(), "未记录"))
                    .append("，词汇 ").append(defaultText(evaluation.getVocabularyScore(), "未记录"))
                    .append("，语法 ").append(defaultText(evaluation.getGrammarScore(), "未记录"))
                    .append("，错误数 ").append(defaultText(evaluation.getTotalErrorCount(), "未记录"))
                    .append("\n");
        }
        input.append("\n写作教练对话：\n");
        for (AssistantConversation conversation : source.conversations()) {
            input.append("会话：").append(defaultText(conversation.getTitle(), conversation.getConversationUid())).append("\n");
            for (AssistantMessage message : source.messagesFor(conversation.getConversationUid())) {
                input.append(roleLabel(message.getRole())).append("：")
                        .append(truncate(cleanCoachMessageForMarkdown(message.getContent()), 1600))
                        .append("\n");
            }
        }
        return input.toString();
    }

    private ParsedLearningAssetPreview parseLearningAssetPreview(
            String rawOutput,
            String runUid,
            Long documentId,
            Long userId) throws JsonProcessingException {
        String json = stripJsonFence(rawOutput);
        JsonNode root = objectMapper.readTree(json);
        String summary = root.path("summary").asText("");
        JsonNode array = root.has("items") ? root.get("items") : root;
        if (!array.isArray()) {
            return new ParsedLearningAssetPreview(summary, List.of());
        }
        List<WritingLearningAssetPreviewItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : array) {
            String assetType = normalizeAssetType(node.path("assetType").asText(""));
            String displayText = node.path("displayText").asText("");
            String recommendedText = node.path("recommendedText").asText("");
            String valueReason = node.path("valueReasonForUser").asText("");
            String dedupeText = normalize(defaultText(recommendedText, displayText));
            if (assetType == null || dedupeText.isBlank() || valueReason.isBlank() || !seen.add(assetType + ":" + dedupeText)) {
                continue;
            }
            WritingLearningAssetPreviewItem item = new WritingLearningAssetPreviewItem();
            item.setItemUid("wlai-" + UUID.randomUUID());
            item.setRunUid(runUid);
            item.setDocumentId(documentId);
            item.setUserId(userId);
            item.setAssetType(assetType);
            item.setSourceType(normalizeSourceType(node.path("sourceType").asText("system_discovered")));
            item.setDisplayText(truncate(defaultText(displayText, recommendedText), 1000));
            item.setOriginalText(truncate(node.path("originalText").asText(""), 1000));
            item.setRecommendedText(truncate(recommendedText, 1000));
            item.setMeaningZh(truncate(node.path("meaningZh").asText(""), 500));
            item.setExplanation(truncate(node.path("explanation").asText(""), 1000));
            item.setValueReasonForUser(truncate(valueReason, 1000));
            item.setHowToReuse(truncate(node.path("howToReuse").asText(""), 1000));
            item.setReviewPrompt(truncate(node.path("reviewPrompt").asText(""), 1000));
            item.setSourceQuestion(truncate(node.path("sourceQuestion").asText(""), 500));
            item.setSourceExcerpt(truncate(node.path("sourceExcerpt").asText(""), 1000));
            item.setConfidence(toScore(node.path("confidence").asDouble(0.7), 1));
            item.setLearningValueScore(toScore(node.path("learningValueScore").asDouble(70), 100));
            item.setPromotionStatus("preview");
            items.add(item);
            if (items.size() >= 12) {
                break;
            }
        }
        return new ParsedLearningAssetPreview(summary, items);
    }

    private WritingDocumentAssetResponse.LearningAssetPreview loadLearningAssetPreview(Long userId, Long documentId) {
        WritingLearningAssetPreviewRun run = assetMapper.findLatestLearningAssetPreviewRun(userId, documentId);
        if (run == null) {
            return WritingDocumentAssetResponse.LearningAssetPreview.none();
        }
        List<WritingLearningAssetPreviewItem> items = assetMapper.selectLearningAssetPreviewItems(run.getRunUid());
        return toLearningAssetPreview(run, items);
    }

    private WritingDocumentAssetResponse.LearningAssetPreview toLearningAssetPreview(
            WritingLearningAssetPreviewRun run,
            List<WritingLearningAssetPreviewItem> items) {
        WritingDocumentAssetResponse.LearningAssetPreview preview = new WritingDocumentAssetResponse.LearningAssetPreview();
        preview.setStatus(defaultText(run.getStatus(), "none"));
        preview.setModel(run.getModel());
        preview.setSummary(run.getSummary());
        preview.setErrorMessage(run.getErrorMessage());
        preview.setGeneratedAt(run.getGeneratedAt());
        preview.setItems((items == null ? List.<WritingLearningAssetPreviewItem>of() : items).stream()
                .map(this::toLearningAssetPreviewItem)
                .toList());
        return preview;
    }

    private WritingDocumentAssetResponse.LearningAssetPreviewItem toLearningAssetPreviewItem(WritingLearningAssetPreviewItem source) {
        WritingDocumentAssetResponse.LearningAssetPreviewItem item = new WritingDocumentAssetResponse.LearningAssetPreviewItem();
        item.setId(source.getItemUid());
        item.setAssetType(source.getAssetType());
        item.setSourceType(source.getSourceType());
        item.setDisplayText(source.getDisplayText());
        item.setOriginalText(source.getOriginalText());
        item.setRecommendedText(source.getRecommendedText());
        item.setMeaningZh(source.getMeaningZh());
        item.setExplanation(source.getExplanation());
        item.setValueReasonForUser(source.getValueReasonForUser());
        item.setHowToReuse(source.getHowToReuse());
        item.setReviewPrompt(source.getReviewPrompt());
        item.setSourceQuestion(source.getSourceQuestion());
        item.setSourceExcerpt(source.getSourceExcerpt());
        item.setConfidence(source.getConfidence() == null ? null : source.getConfidence().doubleValue());
        item.setLearningValueScore(source.getLearningValueScore() == null ? null : source.getLearningValueScore().doubleValue());
        item.setPromotionStatus(source.getPromotionStatus());
        return item;
    }

    private String buildCoachConversationMarkdown(
            Document doc,
            AssistantConversation conversation,
            List<AssistantMessage> messages) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 写作教练对话 - ").append(defaultText(doc.getTitle(), "未命名作文")).append("\n\n");
        markdown.append("## 会话信息\n\n");
        markdown.append("- 作文 ID：`").append(doc.getPublicId()).append("`\n");
        markdown.append("- 会话 ID：`").append(conversation.getConversationUid()).append("`\n");
        markdown.append("- 会话标题：").append(defaultText(conversation.getTitle(), conversation.getConversationUid())).append("\n");
        markdown.append("- 消息数：").append(messages == null ? 0 : messages.size()).append("\n");
        markdown.append("- 更新时间：").append(defaultText(conversation.getUpdatedAt(), "未记录")).append("\n\n");
        markdown.append("## 对话记录\n\n");

        if (messages == null || messages.isEmpty()) {
            markdown.append("暂无写作教练对话。\n");
            return markdown.toString();
        }

        for (AssistantMessage message : messages) {
            markdown.append("### ").append(roleLabel(message.getRole())).append("\n\n");
            markdown.append(cleanCoachMessageForMarkdown(message.getContent())).append("\n\n");
            if (message.getCreatedAt() != null) {
                markdown.append("> 时间：").append(message.getCreatedAt()).append("\n\n");
            }
        }
        return markdown.toString();
    }

    private Map<String, Object> toMetadataSnapshot(WritingMetadata metadata) {
        if (metadata == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", nullToEmpty(metadata.getMode()));
        result.put("studyStage", nullToEmpty(metadata.getStudyStage()));
        result.put("topicTitle", nullToEmpty(metadata.getTopicTitle()));
        result.put("promptText", nullToEmpty(metadata.getPromptText()));
        result.put("genre", nullToEmpty(metadata.getGenre()));
        result.put("sourceType", nullToEmpty(metadata.getSourceType()));
        return result;
    }

    private Map<String, Object> toEvaluationSnapshot(EssayEvaluation evaluation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", evaluation.getId());
        result.put("overallScore", evaluation.getOverallScore());
        result.put("band", nullToEmpty(evaluation.getBand()));
        result.put("structureScore", evaluation.getStructureScore());
        result.put("vocabularyScore", evaluation.getVocabularyScore());
        result.put("grammarScore", evaluation.getGrammarScore());
        result.put("expressionScore", evaluation.getExpressionScore());
        result.put("totalErrorCount", evaluation.getTotalErrorCount());
        result.put("resultJson", nullToEmpty(evaluation.getResultJson()));
        result.put("createdAt", evaluation.getCreatedAt() == null ? "" : evaluation.getCreatedAt().toString());
        return result;
    }

    private Map<String, Object> toMessageSnapshot(AssistantMessage message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", nullToEmpty(message.getMessageUid()));
        result.put("role", nullToEmpty(message.getRole()));
        result.put("content", nullToEmpty(message.getContent()));
        result.put("createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString());
        return result;
    }

    private WritingDocumentAssetResponse.EvaluationItem toEvaluationItem(EssayEvaluation evaluation) {
        WritingDocumentAssetResponse.EvaluationItem item = new WritingDocumentAssetResponse.EvaluationItem();
        item.setId(evaluation.getId());
        item.setOverallScore(evaluation.getOverallScore());
        item.setBand(evaluation.getBand());
        item.setStructureScore(evaluation.getStructureScore());
        item.setVocabularyScore(evaluation.getVocabularyScore());
        item.setGrammarScore(evaluation.getGrammarScore());
        item.setExpressionScore(evaluation.getExpressionScore());
        item.setTotalErrorCount(evaluation.getTotalErrorCount());
        item.setCreatedAt(evaluation.getCreatedAt());
        return item;
    }

    private WritingDocumentAssetResponse.CoachConversationItem toCoachConversationItem(
            AssistantConversation conversation,
            int messageCount) {
        WritingDocumentAssetResponse.CoachConversationItem item = new WritingDocumentAssetResponse.CoachConversationItem();
        item.setId(conversation.getConversationUid());
        item.setTitle(conversation.getTitle());
        item.setMessageCount(messageCount);
        item.setUpdatedAt(conversation.getUpdatedAt());
        return item;
    }

    private boolean isStale(Document doc, WritingDocumentAssetSnapshot snapshot, AssetSource source) {
        int coachMessageCount = source.coachMessages().values().stream().mapToInt(List::size).sum();
        return !equalsInt(snapshot.getLatestRevision(), doc.getLatestRevision())
                || !equalsInt(snapshot.getEvaluationCount(), source.evaluations().size())
                || !equalsInt(snapshot.getCoachMessageCount(), coachMessageCount)
                || (doc.getUpdatedAt() != null && snapshot.getGeneratedAt() != null && doc.getUpdatedAt().isAfter(snapshot.getGeneratedAt()));
    }

    private boolean equalsInt(Integer left, Integer right) {
        return (left == null ? 0 : left) == (right == null ? 0 : right);
    }

    private String cleanCoachMessageForMarkdown(String content) {
        String text = nullToEmpty(content).trim();
        String marker = "[用户本轮问题]";
        int index = text.indexOf(marker);
        if (index >= 0) {
            return text.substring(index + marker.length()).trim();
        }
        return text;
    }

    private String stripJsonFence(String rawOutput) {
        String value = rawOutput == null ? "" : rawOutput.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private String normalizeAssetType(String value) {
        String normalized = normalize(value);
        if ("sentence_pattern".equals(normalized)) {
            return "grammar";
        }
        return LEARNING_ASSET_TYPES.contains(normalized) ? normalized : null;
    }

    private String normalizeSourceType(String value) {
        String normalized = normalize(value);
        if ("user_focus".equals(normalized) || "coach_feedback".equals(normalized) || "system_discovered".equals(normalized)) {
            return normalized;
        }
        return "system_discovered";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private BigDecimal toScore(double value, int max) {
        double normalized = max == 1 ? value : (value > 1 ? value : value * max);
        double clamped = Math.max(0, Math.min(max, normalized));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private String truncate(String value, int maxLength) {
        String text = nullToEmpty(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String roleLabel(String role) {
        if ("user".equals(role)) return "用户";
        if ("assistant".equals(role)) return "写作教练";
        return defaultText(role, "消息");
    }

    private String defaultText(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String defaultText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ParsedLearningAssetPreview(String summary, List<WritingLearningAssetPreviewItem> items) {
    }

    private record AssetSource(
            DocumentRevision revision,
            WritingMetadata metadata,
            List<EssayEvaluation> evaluations,
            long evaluationTotal,
            List<AssistantConversation> conversations,
            Map<String, List<AssistantMessage>> coachMessages) {
        List<AssistantMessage> messagesFor(String conversationUid) {
            return coachMessages.getOrDefault(conversationUid, List.of());
        }
    }
}
