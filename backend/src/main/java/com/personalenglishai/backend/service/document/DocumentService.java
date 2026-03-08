package com.personalenglishai.backend.service.document;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.mapper.DocumentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商用级文档服务：多租户/工作区隔离、版本、乐观锁、软删、owner 权限
 */
@Service
public class DocumentService {

    private static final String WORKSPACE_DEFAULT = "default";

    private final DocumentMapper documentMapper;

    public DocumentService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    /** 生成对外稳定 public_id */
    public static String generatePublicId() {
        return "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateResult createDocument(String tenantId, String workspaceId, Long ownerUserId, String title, String content) {
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
                                                    String taskPrompt, String content) {
        String ws = workspaceId != null && !workspaceId.isBlank() ? workspaceId : WORKSPACE_DEFAULT;
        if (taskPrompt != null && !taskPrompt.isBlank()) {
            String hash = sha256(taskPrompt.trim());
            Document existing = documentMapper.findByOwnerAndPromptHash(ownerUserId, hash, tenantId, ws);
            if (existing != null) {
                // 返回已有文档
                DocumentRevision r = documentMapper.findRevisionByDocumentIdAndRevision(
                        existing.getId(), existing.getLatestRevision());
                String existingContent = r != null ? r.getContent() : null;
                return new StartSessionResult(existing.getPublicId(), existing.getLatestRevision(),
                        false, existingContent, existing.getInitialScore(), existing.getLatestScore(), existing.getSubmitCount());
            }
        }
        // 创建新文档
        CreateResult cr = createDocumentWithPrompt(tenantId, ws, ownerUserId, title, taskPrompt, content);
        return new StartSessionResult(cr.docId, cr.latestRevision, true, null, null, null, 0);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateResult createDocumentWithPrompt(String tenantId, String workspaceId,
                                                  Long ownerUserId, String title,
                                                  String taskPrompt, String content) {
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

    public static final class StartSessionResult {
        public final String docId;
        public final int latestRevision;
        public final boolean isNew;
        public final String existingContent;
        public final Integer initialScore;
        public final Integer latestScore;
        public final Integer submitCount;
        public StartSessionResult(String docId, int latestRevision, boolean isNew, String existingContent,
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
}