package com.personalenglishai.backend.service.document;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.mapper.DocumentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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