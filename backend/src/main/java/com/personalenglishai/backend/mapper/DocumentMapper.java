package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 文档主表 + 版本表 Mapper（商用级：租户/工作区隔离、乐观锁、软删）
 */
@Mapper
public interface DocumentMapper {

    int insertDocument(Document doc);

    Document findByPublicIdAndTenantAndWorkspace(
            @Param("publicId") String publicId,
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId);

    /** 含已删除，用于 restore */
    Document findByPublicIdAndTenantAndWorkspaceIncludeDeleted(
            @Param("publicId") String publicId,
            @Param("tenantId") String tenantId,
            @Param("workspaceId") String workspaceId);

    /** 乐观锁：仅当 latest_revision = expected 时更新 */
    int updateLatestRevision(
            @Param("id") Long documentId,
            @Param("expectedLatestRevision") int expectedLatestRevision,
            @Param("newLatestRevision") int newLatestRevision);

    int insertRevision(DocumentRevision rev);

    DocumentRevision findRevisionByDocumentIdAndRevision(
            @Param("documentId") Long documentId,
            @Param("revision") int revision);

    int softDelete(@Param("id") Long documentId, @Param("deletedAt") LocalDateTime deletedAt);

    int restore(@Param("id") Long documentId);
}
