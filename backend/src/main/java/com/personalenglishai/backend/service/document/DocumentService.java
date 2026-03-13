package com.personalenglishai.backend.service.document;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.entity.WritingExamMetadata;
import com.personalenglishai.backend.entity.WritingMetadata;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.WritingExamMetadataMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentService {

    private static final String WORKSPACE_DEFAULT = "default";

    private static final int WRITING_METADATA_TITLE_MAX_LEN = 255;

    private final DocumentMapper documentMapper;
    private final WritingMetadataMapper writingMetadataMapper;
    private final WritingExamMetadataMapper writingExamMetadataMapper;

    public DocumentService(DocumentMapper documentMapper,
                          WritingMetadataMapper writingMetadataMapper,
                          WritingExamMetadataMapper writingExamMetadataMapper) {
        this.documentMapper = documentMapper;
        this.writingMetadataMapper = writingMetadataMapper;
        this.writingExamMetadataMapper = writingExamMetadataMapper;
    }

    /** 生成对外稳定 public_id */
    public static String generatePublicId() {
        return "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateResult createDocument(String tenantId, String workspaceId, Long ownerUserId, String title, String content) {
        StartMetadata metadata = new StartMetadata();
        metadata.setMode("free");
        metadata.setTitleSnapshot(title);
        metadata.setSourceType("free_input");
        metadata.setTopicTitle(null);
        metadata.setPromptText(null);
        metadata.setGenre(null);
        metadata.setStudyStage(null);
        metadata.setExamType(null);
        metadata.setTaskType(null);
        metadata.setMinWords(null);
        metadata.setRecommendedMaxWords(null);
        metadata.setMaxScore(null);
        return createDocument(tenantId, workspaceId, ownerUserId, title, content, metadata);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateResult createDocument(String tenantId, String workspaceId, Long ownerUserId,
                                     String title, String content, StartMetadata metadata) {
        String publicId = generatePublicId();
        Document doc = new Document();
        doc.setPublicId(publicId);
        doc.setTenantId(tenantId);
        doc.setWorkspaceId(workspaceId != null && !workspaceId.isBlank() ? workspaceId : WORKSPACE_DEFAULT);
        doc.setOwnerUserId(ownerUserId);
        doc.setTitle(title != null ? title : "");
        doc.setStatus(0);
        doc.setLatestRevision(1);
        documentMapper.insertDocument(doc);

        DocumentRevision rev = new DocumentRevision();
        rev.setDocumentId(doc.getId());
        rev.setRevision(1);
        rev.setContent(content != null ? content : "");
        rev.setCreatedBy(ownerUserId);
        documentMapper.insertRevision(rev);

        upsertWritingMetadata(doc, ownerUserId, metadata);

        return new CreateResult(publicId, 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppendResult appendRevision(String tenantId, String workspaceId, String publicDocId, int expectedLatestRevision, String content, Long userId) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND, "document not found");
        }
        if (!doc.getOwnerUserId().equals(userId)) {
            throw new BizException(ErrorCode.DOC_FORBIDDEN, "not owner");
        }
        if (!doc.getLatestRevision().equals(expectedLatestRevision)) {
            throw new BizException(ErrorCode.DOC_CONFLICT, "revision conflict: expected " + expectedLatestRevision + ", actual " + doc.getLatestRevision());
        }
        int newRev = expectedLatestRevision + 1;
        DocumentRevision rev = new DocumentRevision();
        rev.setDocumentId(doc.getId());
        rev.setRevision(newRev);
        rev.setContent(content != null ? content : "");
        rev.setCreatedBy(userId);
        documentMapper.insertRevision(rev);

        int updated = documentMapper.updateLatestRevision(doc.getId(), expectedLatestRevision, newRev);
        if (updated == 0) {
            throw new BizException(ErrorCode.DOC_CONFLICT, "revision conflict");
        }
        return new AppendResult(newRev);
    }

    public Optional<DocContent> getLatestContent(String tenantId, String workspaceId, String publicDocId, Long userId) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) return Optional.empty();
        if (!doc.getOwnerUserId().equals(userId)) return Optional.empty();
        DocumentRevision r = documentMapper.findRevisionByDocumentIdAndRevision(doc.getId(), doc.getLatestRevision());
        if (r == null) return Optional.empty();
        return Optional.of(new DocContent(doc.getTitle(), doc.getLatestRevision(), r.getContent()));
    }

    public Optional<DocContent> getContentByRevision(String tenantId, String workspaceId, String publicDocId, int revision, Long userId) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) return Optional.empty();
        if (!doc.getOwnerUserId().equals(userId)) return Optional.empty();
        DocumentRevision r = documentMapper.findRevisionByDocumentIdAndRevision(doc.getId(), revision);
        if (r == null) return Optional.empty();
        return Optional.of(new DocContent(doc.getTitle(), revision, r.getContent()));
    }

    /** 供 AI ContextBuilder 使用：按 docId(+revision) 取内容，仅校验租户/工作区与未删除，不强制 owner（由调用方保证） */
    public Optional<DocContent> getContentForAI(String tenantId, String workspaceId, String publicDocId, Integer revision) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) return Optional.empty();
        int rev = (revision != null && revision > 0) ? revision : doc.getLatestRevision();
        DocumentRevision r = documentMapper.findRevisionByDocumentIdAndRevision(doc.getId(), rev);
        if (r == null) return Optional.empty();
        return Optional.of(new DocContent(doc.getTitle(), rev, r.getContent()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDelete(String tenantId, String workspaceId, String publicDocId, Long userId) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) throw new BizException(ErrorCode.DOC_NOT_FOUND, "document not found");
        if (!doc.getOwnerUserId().equals(userId)) throw new BizException(ErrorCode.DOC_FORBIDDEN, "not owner");
        documentMapper.softDelete(doc.getId(), LocalDateTime.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(String tenantId, String workspaceId, String publicDocId, Long userId, String newTitle) {
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
        if (doc == null) throw new BizException(ErrorCode.DOC_NOT_FOUND, "document not found");
        if (!doc.getOwnerUserId().equals(userId)) throw new BizException(ErrorCode.DOC_FORBIDDEN, "not owner");
        documentMapper.updateTitle(doc.getId(), newTitle);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(String tenantId, String workspaceId, String publicDocId, Long userId) {
        Document doc = findIncludeDeleted(tenantId, workspaceId, publicDocId);
        if (doc == null) throw new BizException(ErrorCode.DOC_NOT_FOUND, "document not found");
        if (doc.getDeletedAt() == null) return;
        if (!doc.getOwnerUserId().equals(userId)) throw new BizException(ErrorCode.DOC_FORBIDDEN, "not owner");
        documentMapper.restore(doc.getId());
    }

    /** 含已删除的查询，用于 restore */
    public Document findIncludeDeleted(String tenantId, String workspaceId, String publicDocId) {
        return documentMapper.findByPublicIdAndTenantAndWorkspaceIncludeDeleted(publicDocId, tenantId, workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
    }

    /**
     * 根据题目查找已有文档或创建新文档
     */
    @Transactional(rollbackFor = Exception.class)
    public StartSessionResult findOrCreateForTopic(String tenantId, String workspaceId,
                                                  Long ownerUserId, String title,
                                                  String taskPrompt, String content,
                                                  StartMetadata metadata) {
        String ws = workspaceId != null && !workspaceId.isBlank() ? workspaceId : WORKSPACE_DEFAULT;
        if (taskPrompt != null && !taskPrompt.isBlank()) {
            String hash = sha256(taskPrompt.trim());
            Document existing = documentMapper.findByOwnerAndPromptHash(ownerUserId, hash, tenantId, ws);
            if (existing != null) {
                // 返回已有文档（若有元数据更新则补齐）
                if (metadata != null) {
                    upsertWritingMetadata(existing, ownerUserId, metadata);
                }
                DocumentRevision r = documentMapper.findRevisionByDocumentIdAndRevision(
                        existing.getId(), existing.getLatestRevision());
                String existingContent = r != null ? r.getContent() : null;
                return new StartSessionResult(existing.getPublicId(), existing.getLatestRevision(),
                        false, existingContent, existing.getInitialScore(), existing.getLatestScore(), existing.getSubmitCount());
            }
        }
        // 创建新文档
        CreateResult cr = createDocumentWithPrompt(tenantId, ws, ownerUserId, title, taskPrompt, content, metadata);
        return new StartSessionResult(cr.docId, cr.latestRevision, true, null, null, null, 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateResult createDocumentWithPrompt(String tenantId, String workspaceId,
                                                  Long ownerUserId, String title,
                                                  String taskPrompt, String content) {
        StartMetadata metadata = new StartMetadata();
        metadata.setMode("exam");
        metadata.setTitleSnapshot(title);
        metadata.setPromptText(taskPrompt);
        metadata.setSourceType("manual");
        return createDocumentWithPrompt(tenantId, workspaceId, ownerUserId, title, taskPrompt, content, metadata);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateResult createDocumentWithPrompt(String tenantId, String workspaceId,
                                                  Long ownerUserId, String title,
                                                  String taskPrompt, String content,
                                                  StartMetadata metadata) {
        String publicId = generatePublicId();
        String ws = workspaceId != null && !workspaceId.isBlank() ? workspaceId : WORKSPACE_DEFAULT;
        Document doc = new Document();
        doc.setPublicId(publicId);
        doc.setTenantId(tenantId);
        doc.setWorkspaceId(ws);
        doc.setOwnerUserId(ownerUserId);
        doc.setTitle(title != null ? title : "");
        doc.setTaskPrompt(taskPrompt);
        doc.setTaskPromptHash(taskPrompt != null && !taskPrompt.isBlank() ? sha256(taskPrompt.trim()) : null);
        doc.setSubmitCount(0);
        doc.setStatus(0);
        doc.setLatestRevision(1);
        documentMapper.insertDocument(doc);

        DocumentRevision rev = new DocumentRevision();
        rev.setDocumentId(doc.getId());
        rev.setRevision(1);
        rev.setContent(content != null ? content : "");
        rev.setCreatedBy(ownerUserId);
        documentMapper.insertRevision(rev);

        upsertWritingMetadata(doc, ownerUserId, metadata);

        return new CreateResult(publicId, 1);
    }

    public void updateScoreStats(Long documentId, Integer score) {
        documentMapper.updateScoreStats(documentId, score);
    }

    /** 将文档状态从草稿(0)激活为正式(1) */
    public void activateIfDraft(Long documentId) {
        documentMapper.updateStatus(documentId, 1);
    }

    public Document findByPublicId(String tenantId, String workspaceId, String publicDocId) {
        return documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId,
                workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
    }

    public com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse getSessionMetadataByDocId(
            String tenantId, String workspaceId, String publicDocId, Long ownerUserId) {
        String ws = workspaceId != null && !workspaceId.isBlank() ? workspaceId : WORKSPACE_DEFAULT;
        Document doc = documentMapper.findByPublicIdAndTenantAndWorkspace(publicDocId, tenantId, ws);
        if (doc == null || !doc.getOwnerUserId().equals(ownerUserId)) {
            return null;
        }

        WritingMetadata metadata = writingMetadataMapper.selectByDocumentId(doc.getId());
        if (metadata == null) {
            return null;
        }

        WritingSessionMetadataResponse response = new WritingSessionMetadataResponse();
        response.setDocumentId(publicDocId);
        response.setMetadataId(metadata.getId());
        response.setMode(metadata.getMode());
        response.setStudyStage(metadata.getStudyStage());
        response.setTitleSnapshot(metadata.getTitleSnapshot());
        response.setTopicTitle(metadata.getTopicTitle());
        response.setPromptText(metadata.getPromptText());
        response.setGenre(metadata.getGenre());
        response.setSourceType(metadata.getSourceType());
        response.setCreatedAt(metadata.getCreatedAt());
        response.setUpdatedAt(metadata.getUpdatedAt());

        if ("exam".equals(metadata.getMode())) {
            WritingExamMetadata examMetadata = writingExamMetadataMapper.selectByMetadataId(metadata.getId());
            if (examMetadata != null) {
                response.setExamMetadataId(examMetadata.getId());
                response.setExamType(examMetadata.getExamType());
                response.setTaskType(examMetadata.getTaskType());
                response.setMinWords(examMetadata.getMinWords());
                response.setRecommendedMaxWords(examMetadata.getRecommendedMaxWords());
                response.setMaxScore(examMetadata.getMaxScore());
            }
        }

        return response;
    }
    public List<Document> listByOwner(String tenantId, String workspaceId, Long ownerUserId, int offset, int limit) {
        return documentMapper.listByOwnerUserId(ownerUserId, tenantId,
                workspaceId != null ? workspaceId : WORKSPACE_DEFAULT, offset, limit);
    }

    public long countByOwner(String tenantId, String workspaceId, Long ownerUserId) {
        return documentMapper.countByOwnerUserId(ownerUserId, tenantId,
                workspaceId != null ? workspaceId : WORKSPACE_DEFAULT);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public void upsertWritingMetadata(Document doc, Long ownerUserId, StartMetadata metadata) {
        if (metadata == null || doc == null) {
            return;
        }

        String mode = normalizeMode(metadata.getMode());
        String sourceType = normalizeSourceType(metadata.getSourceType(), mode);

        String requestTitleSnapshot = normalizeTextToMax(metadata.getTitleSnapshot(), WRITING_METADATA_TITLE_MAX_LEN);
        String fallbackTitleSnapshot = normalizeTextToMax(doc.getTitle(), WRITING_METADATA_TITLE_MAX_LEN);
        String resolvedTitle = coalesce(requestTitleSnapshot, fallbackTitleSnapshot, "");

        WritingMetadata existingMetadata = writingMetadataMapper.selectByDocumentId(doc.getId());
        String resolvedStudyStage = trimToNull(metadata.getStudyStage());
        String resolvedTopicTitle = normalizeTextToMax(metadata.getTopicTitle(), WRITING_METADATA_TITLE_MAX_LEN);
        String resolvedPromptText = trimToNull(metadata.getPromptText());
        String resolvedGenre = trimToNull(metadata.getGenre());

        WritingMetadata target = existingMetadata;
        if (existingMetadata == null) {
            WritingMetadata newMetadata = new WritingMetadata();
            newMetadata.setDocumentId(doc.getId());
            newMetadata.setUserId(ownerUserId);
            newMetadata.setMode(mode);
            newMetadata.setStudyStage(resolvedStudyStage);
            newMetadata.setTitleSnapshot(resolvedTitle);
            newMetadata.setTopicTitle(resolvedTopicTitle);
            newMetadata.setPromptText(resolvedPromptText);
            newMetadata.setGenre(resolvedGenre);
            newMetadata.setSourceType(sourceType);
            writingMetadataMapper.insert(newMetadata);
            target = newMetadata;
        } else {
            existingMetadata.setUserId(ownerUserId);
            existingMetadata.setMode(mode);
            existingMetadata.setStudyStage(coalesceString(resolvedStudyStage, existingMetadata.getStudyStage()));
            existingMetadata.setTitleSnapshot(coalesceString(resolvedTitle, existingMetadata.getTitleSnapshot()));
            existingMetadata.setTopicTitle(coalesceString(resolvedTopicTitle, existingMetadata.getTopicTitle()));
            existingMetadata.setPromptText(coalesceString(resolvedPromptText, existingMetadata.getPromptText()));
            existingMetadata.setGenre(coalesceString(resolvedGenre, existingMetadata.getGenre()));
            existingMetadata.setSourceType(coalesceString(sourceType, existingMetadata.getSourceType()));
            writingMetadataMapper.updateByDocumentId(existingMetadata);
        }

        if (target != null && "exam".equals(mode)) {
            upsertWritingExamMetadata(target, metadata);
        }
    }

    private void upsertWritingExamMetadata(WritingMetadata metadata, StartMetadata requestMetadata) {
        WritingExamMetadata existing = writingExamMetadataMapper.selectByMetadataId(metadata.getId());
        if (existing == null) {
            WritingExamMetadata examMetadata = new WritingExamMetadata();
            examMetadata.setMetadataId(metadata.getId());
            examMetadata.setExamType(trimToNull(requestMetadata.getExamType()));
            examMetadata.setTaskType(trimToNull(requestMetadata.getTaskType()));
            examMetadata.setMinWords(requestMetadata.getMinWords());
            examMetadata.setRecommendedMaxWords(requestMetadata.getRecommendedMaxWords());
            examMetadata.setMaxScore(requestMetadata.getMaxScore());
            writingExamMetadataMapper.insert(examMetadata);
            return;
        }

        existing.setExamType(coalesceString(trimToNull(requestMetadata.getExamType()), existing.getExamType()));
        existing.setTaskType(coalesceString(trimToNull(requestMetadata.getTaskType()), existing.getTaskType()));
        existing.setMinWords(coalesceInteger(requestMetadata.getMinWords(), existing.getMinWords()));
        existing.setRecommendedMaxWords(coalesceInteger(requestMetadata.getRecommendedMaxWords(), existing.getRecommendedMaxWords()));
        existing.setMaxScore(coalesceInteger(requestMetadata.getMaxScore(), existing.getMaxScore()));
        writingExamMetadataMapper.updateByMetadataId(existing);
    }

    private String coalesceString(String primary, String fallback) {
        return primary == null ? fallback : primary;
    }

    private Integer coalesceInteger(Integer primary, Integer fallback) {
        return primary == null ? fallback : primary;
    }

    private String coalesce(String first, String second, String fallback) {
        return first != null ? first : (second != null ? second : fallback);
    }

    private String normalizeTextToMax(String raw, int maxLen) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }
    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "free";
        }
        String normalized = mode.trim().toLowerCase();
        return "exam".equals(normalized) ? "exam" : "free";
    }

    private String normalizeSourceType(String sourceType, String mode) {
        if (sourceType == null) {
            return "exam".equals(mode) ? "manual" : "free_input";
        }
        String normalized = sourceType.trim().toLowerCase();
        return switch (normalized) {
            case "manual", "past_prompt", "ai_generated", "free_input" -> normalized;
            default -> "exam".equals(mode) ? "manual" : "free_input";
        };
    }

    private String trimToNull(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        return value.isBlank() ? null : value;
    }

    private String trimToMax(String raw, int maxLen) {
        String value = trimToNull(raw);
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    public static final class StartSessionResult {
        public final String docId;
        public final int latestRevision;
        public final boolean isNew;
        public final String existingContent;
        public final Integer initialScore;
        public final Integer latestScore;
        public final Integer submitCount;
        StartSessionResult(String docId, int latestRevision, boolean isNew, String existingContent,
                                   Integer initialScore, Integer latestScore, Integer submitCount) {
            this.docId = docId; this.latestRevision = latestRevision; this.isNew = isNew;
            this.existingContent = existingContent; this.initialScore = initialScore;
            this.latestScore = latestScore; this.submitCount = submitCount != null ? submitCount : 0;
        }
    }

    public static final class CreateResult {
        public final String docId;
        public final int latestRevision;
        public CreateResult(String docId, int latestRevision) { this.docId = docId; this.latestRevision = latestRevision; }
    }

    public static final class AppendResult {
        public final int latestRevision;
        public AppendResult(int latestRevision) { this.latestRevision = latestRevision; }
    }

    public static final class DocContent {
        public final String title;
        public final int revision;
        public final String content;
        public DocContent(String title, int revision, String content) { this.title = title; this.revision = revision; this.content = content; }
    }

    public static final class StartMetadata {
        private String mode;
        private String studyStage;
        private String titleSnapshot;
        private String topicTitle;
        private String promptText;
        private String genre;
        private String sourceType;
        private String examType;
        private String taskType;
        private Integer minWords;
        private Integer recommendedMaxWords;
        private Integer maxScore;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getStudyStage() {
            return studyStage;
        }

        public void setStudyStage(String studyStage) {
            this.studyStage = studyStage;
        }

        public String getTitleSnapshot() {
            return titleSnapshot;
        }

        public void setTitleSnapshot(String titleSnapshot) {
            this.titleSnapshot = titleSnapshot;
        }

        public String getTopicTitle() {
            return topicTitle;
        }

        public void setTopicTitle(String topicTitle) {
            this.topicTitle = topicTitle;
        }

        public String getPromptText() {
            return promptText;
        }

        public void setPromptText(String promptText) {
            this.promptText = promptText;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getExamType() {
            return examType;
        }

        public void setExamType(String examType) {
            this.examType = examType;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public Integer getMinWords() {
            return minWords;
        }

        public void setMinWords(Integer minWords) {
            this.minWords = minWords;
        }

        public Integer getRecommendedMaxWords() {
            return recommendedMaxWords;
        }

        public void setRecommendedMaxWords(Integer recommendedMaxWords) {
            this.recommendedMaxWords = recommendedMaxWords;
        }

        public Integer getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(Integer maxScore) {
            this.maxScore = maxScore;
        }
    }
}






