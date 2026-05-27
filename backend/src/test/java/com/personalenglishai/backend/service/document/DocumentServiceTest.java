package com.personalenglishai.backend.service.document;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.writing.BindHandwritingImportRequest;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.entity.WritingMetadata;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.WritingExamMetadataMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
import com.personalenglishai.backend.dto.writing.WritingSessionMetadataResponse;
import com.personalenglishai.backend.service.writing.WritingPromptSheetService;
import com.personalenglishai.backend.service.writing.WritingDocumentAssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private WritingMetadataMapper writingMetadataMapper;

    @Mock
    private WritingExamMetadataMapper writingExamMetadataMapper;

    @Mock
    private WritingPromptSheetService writingPromptSheetService;

    @Mock
    private WritingDocumentAssetService writingDocumentAssetService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    @DisplayName("createDocument uses default workspace and inserts initial revision")
    void createDocument_success() {
        doAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(10L);
            return 1;
        }).when(documentMapper).insertDocument(any(Document.class));

        DocumentService.CreateResult result = documentService.createDocument("tenant-1", null, 1L, null, null);

        assertThat(result.docId).startsWith("doc_");
        assertThat(result.latestRevision).isEqualTo(1);

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).insertDocument(docCaptor.capture());
        Document insertedDoc = docCaptor.getValue();
        assertThat(insertedDoc.getTenantId()).isEqualTo("tenant-1");
        assertThat(insertedDoc.getWorkspaceId()).isEqualTo("default");
        assertThat(insertedDoc.getOwnerUserId()).isEqualTo(1L);
        assertThat(insertedDoc.getTitle()).isEqualTo("");
        assertThat(insertedDoc.getLatestRevision()).isEqualTo(1);

        ArgumentCaptor<DocumentRevision> revCaptor = ArgumentCaptor.forClass(DocumentRevision.class);
        verify(documentMapper).insertRevision(revCaptor.capture());
        DocumentRevision insertedRev = revCaptor.getValue();
        assertThat(insertedRev.getDocumentId()).isEqualTo(10L);
        assertThat(insertedRev.getRevision()).isEqualTo(1);
        assertThat(insertedRev.getContent()).isEqualTo("");
        assertThat(insertedRev.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createDocumentWithPrompt binds prompt sheet id onto document")
    void createDocumentWithPrompt_bindsPromptSheetId() {
        doAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(11L);
            return 1;
        }).when(documentMapper).insertDocument(any(Document.class));

        DocumentService.StartMetadata metadata = new DocumentService.StartMetadata();
        metadata.setMode("exam");
        metadata.setPromptSheetId(88L);
        metadata.setPromptText("Write an essay based on the chart below.");

        documentService.createDocumentWithPrompt("tenant-1", "default", 1L, "折线图作文", "Write an essay based on the chart below.", "", metadata);

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).insertDocument(docCaptor.capture());
        assertThat(docCaptor.getValue().getPromptSheetId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("findOrCreateForTopic updates prompt sheet id when reusing existing document")
    void findOrCreateForTopic_updatesPromptSheetIdForExistingDocument() {
        Document existing = buildDoc(20L, 1L, 1);
        existing.setPublicId("doc_existing");
        when(documentMapper.findByOwnerAndPromptHash(eq(1L), anyString(), eq("tenant-1"), eq("default"))).thenReturn(existing);
        when(writingMetadataMapper.selectByDocumentId(20L)).thenReturn(null);

        DocumentRevision rev = new DocumentRevision();
        rev.setContent("existing");
        when(documentMapper.findRevisionByDocumentIdAndRevision(20L, 1)).thenReturn(rev);

        DocumentService.StartMetadata metadata = new DocumentService.StartMetadata();
        metadata.setMode("exam");
        metadata.setPromptSheetId(99L);
        metadata.setPromptText("same prompt");

        DocumentService.StartSessionResult result = documentService.findOrCreateForTopic(
                "tenant-1",
                "default",
                1L,
                "题目",
                "hash",
                "",
                metadata
        );

        assertThat(result.docId).isEqualTo("doc_existing");
        verify(documentMapper).updatePromptSheetId(20L, 99L);
        verify(writingPromptSheetService).bindDocument(99L, 20L);
    }

    @Test
    @DisplayName("bindHandwritingImport updates existing metadata with latest handwriting fields")
    void bindHandwritingImport_updatesExistingMetadata() {
        Document doc = buildDoc(30L, 1L, 1);
        doc.setPublicId("doc_handwriting");
        doc.setTaskPrompt("Write about your hometown");
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_handwriting", "tenant-1", "default"))
                .thenReturn(doc);

        WritingMetadata metadata = new WritingMetadata();
        metadata.setId(88L);
        metadata.setDocumentId(30L);
        metadata.setUserId(1L);
        metadata.setMode("exam");
        metadata.setTitleSnapshot("Original title");
        metadata.setTopicTitle("Original topic");
        metadata.setPromptText("Original prompt");
        metadata.setSourceType("manual");
        metadata.setCreatedAt(LocalDateTime.of(2026, 4, 12, 9, 0));
        when(writingMetadataMapper.selectByDocumentId(30L)).thenReturn(metadata);

        BindHandwritingImportRequest request = new BindHandwritingImportRequest();
        request.setDocId("doc_handwriting");
        request.setSourceType("image");
        request.setImageUrl("https://example.com/handwriting.png");
        request.setRecognizedText("recognized paragraph");

        documentService.bindHandwritingImport("tenant-1", "default", request, 1L);

        ArgumentCaptor<WritingMetadata> metadataCaptor = ArgumentCaptor.forClass(WritingMetadata.class);
        verify(writingMetadataMapper).updateByDocumentId(metadataCaptor.capture());
        WritingMetadata updated = metadataCaptor.getValue();
        assertThat(updated.getDocumentId()).isEqualTo(30L);
        assertThat(updated.getHandwrittenSourceType()).isEqualTo("image");
        assertThat(updated.getHandwrittenSourceImageUrl()).isEqualTo("https://example.com/handwriting.png");
        assertThat(updated.getHandwrittenRecognizedText()).isEqualTo("recognized paragraph");
        assertThat(updated.getHandwrittenImportedAt()).isNotNull();
        assertThat(updated.getTitleSnapshot()).isEqualTo("Original title");
        verify(writingMetadataMapper, never()).insert(any(WritingMetadata.class));
    }

    @Test
    @DisplayName("bindHandwritingImport inserts minimal metadata when missing")
    void bindHandwritingImport_insertsMinimalMetadata() {
        Document doc = buildDoc(40L, 1L, 1);
        doc.setPublicId("doc_new");
        doc.setTaskPrompt("Write an essay based on the chart below.");
        doc.setPromptSheetId(501L);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_new", "tenant-1", "default"))
                .thenReturn(doc);
        when(writingMetadataMapper.selectByDocumentId(40L)).thenReturn(null);

        documentService.bindHandwritingImport(
                "tenant-1",
                "default",
                "doc_new",
                1L,
                "photo",
                "https://example.com/import.png",
                "recognized text"
        );

        ArgumentCaptor<WritingMetadata> metadataCaptor = ArgumentCaptor.forClass(WritingMetadata.class);
        verify(writingMetadataMapper).insert(metadataCaptor.capture());
        WritingMetadata inserted = metadataCaptor.getValue();
        assertThat(inserted.getDocumentId()).isEqualTo(40L);
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getMode()).isEqualTo("exam");
        assertThat(inserted.getSourceType()).isEqualTo("manual");
        assertThat(inserted.getHandwrittenSourceType()).isEqualTo("photo");
        assertThat(inserted.getHandwrittenSourceImageUrl()).isEqualTo("https://example.com/import.png");
        assertThat(inserted.getHandwrittenRecognizedText()).isEqualTo("recognized text");
        assertThat(inserted.getHandwrittenImportedAt()).isNotNull();
    }

    @Test
    @DisplayName("getSessionMetadataByDocId returns handwritten import fields")
    void getSessionMetadataByDocId_includesHandwrittenFields() {
        Document doc = buildDoc(50L, 1L, 1);
        doc.setPublicId("doc_meta");
        doc.setPromptSheetId(501L);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_meta", "tenant-1", "default"))
                .thenReturn(doc);

        WritingMetadata metadata = new WritingMetadata();
        metadata.setId(90L);
        metadata.setDocumentId(50L);
        metadata.setUserId(1L);
        metadata.setMode("exam");
        metadata.setTitleSnapshot("Title");
        metadata.setTopicTitle("Topic");
        metadata.setPromptText("Prompt");
        metadata.setSourceType("manual");
        metadata.setHandwrittenSourceType("image");
        metadata.setHandwrittenSourceImageUrl("https://example.com/meta.png");
        metadata.setHandwrittenRecognizedText("recognized text");
        metadata.setHandwrittenImportedAt(LocalDateTime.of(2026, 4, 12, 12, 0));
        metadata.setCreatedAt(LocalDateTime.of(2026, 4, 12, 11, 59));
        metadata.setUpdatedAt(LocalDateTime.of(2026, 4, 12, 12, 1));
        when(writingMetadataMapper.selectByDocumentId(50L)).thenReturn(metadata);

        WritingSessionMetadataResponse response =
                documentService.getSessionMetadataByDocId("tenant-1", "default", "doc_meta", 1L);

        assertThat(response).isNotNull();
        assertThat(response.getLatestHandwrittenSourceType()).isEqualTo("image");
        assertThat(response.getLatestHandwrittenSourceImageUrl()).isEqualTo("https://example.com/meta.png");
        assertThat(response.getLatestHandwrittenRecognizedText()).isEqualTo("recognized text");
        assertThat(response.getLatestHandwrittenImportedAt()).isEqualTo(LocalDateTime.of(2026, 4, 12, 12, 0));
    }

    @Test
    @DisplayName("appendRevision throws DOC_NOT_FOUND when document is missing")
    void appendRevision_notFound() {
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(null);

        assertThatThrownBy(() -> documentService.appendRevision("1", "default", "doc_x", 1, "v2", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_NOT_FOUND);
    }

    @Test
    @DisplayName("appendRevision throws DOC_FORBIDDEN when user is not owner")
    void appendRevision_forbidden() {
        Document doc = buildDoc(10L, 2L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        assertThatThrownBy(() -> documentService.appendRevision("1", "default", "doc_x", 1, "v2", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_FORBIDDEN);
    }

    @Test
    @DisplayName("appendRevision throws DOC_CONFLICT when expected revision mismatches")
    void appendRevision_expectedRevisionMismatch() {
        Document doc = buildDoc(10L, 1L, 3);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        assertThatThrownBy(() -> documentService.appendRevision("1", "default", "doc_x", 2, "v4", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_CONFLICT);
    }

    @Test
    @DisplayName("appendRevision throws DOC_CONFLICT when optimistic lock update fails")
    void appendRevision_updateLatestFailed() {
        Document doc = buildDoc(10L, 1L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);
        when(documentMapper.updateLatestRevision(10L, 1, 2)).thenReturn(0);

        assertThatThrownBy(() -> documentService.appendRevision("1", "default", "doc_x", 1, "v2", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_CONFLICT);
    }

    @Test
    @DisplayName("appendRevision inserts new revision and returns new latest revision")
    void appendRevision_success() {
        Document doc = buildDoc(10L, 1L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);
        when(documentMapper.updateLatestRevision(10L, 1, 2)).thenReturn(1);

        DocumentService.AppendResult result =
                documentService.appendRevision("1", "default", "doc_x", 1, "v2", 1L);

        assertThat(result.latestRevision).isEqualTo(2);

        ArgumentCaptor<DocumentRevision> revCaptor = ArgumentCaptor.forClass(DocumentRevision.class);
        verify(documentMapper).insertRevision(revCaptor.capture());
        DocumentRevision inserted = revCaptor.getValue();
        assertThat(inserted.getDocumentId()).isEqualTo(10L);
        assertThat(inserted.getRevision()).isEqualTo(2);
        assertThat(inserted.getContent()).isEqualTo("v2");
        assertThat(inserted.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getLatestContent returns empty when requester is not owner")
    void getLatestContent_notOwner() {
        Document doc = buildDoc(10L, 2L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        Optional<DocumentService.DocContent> result =
                documentService.getLatestContent("1", "default", "doc_x", 1L);

        assertThat(result).isEmpty();
        verify(documentMapper, never()).findRevisionByDocumentIdAndRevision(any(Long.class), any(Integer.class));
    }

    @Test
    @DisplayName("getLatestContent returns document content for owner")
    void getLatestContent_success() {
        Document doc = buildDoc(10L, 1L, 2);
        DocumentRevision rev = new DocumentRevision();
        rev.setDocumentId(10L);
        rev.setRevision(2);
        rev.setContent("v2");
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);
        when(documentMapper.findRevisionByDocumentIdAndRevision(10L, 2)).thenReturn(rev);

        Optional<DocumentService.DocContent> result =
                documentService.getLatestContent("1", "default", "doc_x", 1L);

        assertThat(result).isPresent();
        assertThat(result.get().title).isEqualTo("My Doc");
        assertThat(result.get().revision).isEqualTo(2);
        assertThat(result.get().content).isEqualTo("v2");
    }

    @Test
    @DisplayName("softDelete throws DOC_NOT_FOUND when document is missing")
    void softDelete_notFound() {
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(null);

        assertThatThrownBy(() -> documentService.softDelete("1", "default", "doc_x", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_NOT_FOUND);
    }

    @Test
    @DisplayName("softDelete throws DOC_FORBIDDEN when requester is not owner")
    void softDelete_forbidden() {
        Document doc = buildDoc(10L, 2L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        assertThatThrownBy(() -> documentService.softDelete("1", "default", "doc_x", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_FORBIDDEN);
    }

    @Test
    @DisplayName("softDelete calls mapper when requester is owner")
    void softDelete_success() {
        Document doc = buildDoc(10L, 1L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        documentService.softDelete("1", "default", "doc_x", 1L);

        verify(documentMapper).softDelete(eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("archiveDocument marks owned document as archived")
    void archiveDocument_success() {
        Document doc = buildDoc(10L, 1L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        documentService.archiveDocument("1", "default", "doc_x", 1L);

        verify(documentMapper).updateStatus(10L, 2);
        verify(writingDocumentAssetService).refreshSnapshot("1", "default", "doc_x", 1L);
    }

    @Test
    @DisplayName("archiveDocument throws DOC_FORBIDDEN when requester is not owner")
    void archiveDocument_forbidden() {
        Document doc = buildDoc(10L, 2L, 1);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        assertThatThrownBy(() -> documentService.archiveDocument("1", "default", "doc_x", 1L))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode() == ErrorCode.DOC_FORBIDDEN);

        verify(documentMapper, never()).updateStatus(any(Long.class), any(Integer.class));
    }

    @Test
    @DisplayName("unarchiveDocument restores owned archived document to active")
    void unarchiveDocument_success() {
        Document doc = buildDoc(10L, 1L, 1);
        doc.setStatus(2);
        when(documentMapper.findByPublicIdAndTenantAndWorkspace("doc_x", "1", "default")).thenReturn(doc);

        documentService.unarchiveDocument("1", "default", "doc_x", 1L);

        verify(documentMapper).updateStatus(10L, 1);
    }

    private Document buildDoc(Long id, Long ownerUserId, int latestRevision) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerUserId(ownerUserId);
        doc.setTitle("My Doc");
        doc.setLatestRevision(latestRevision);
        return doc;
    }
}

