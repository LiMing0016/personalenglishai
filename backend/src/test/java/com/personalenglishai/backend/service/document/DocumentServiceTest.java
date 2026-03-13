package com.personalenglishai.backend.service.document;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.Document;
import com.personalenglishai.backend.entity.DocumentRevision;
import com.personalenglishai.backend.mapper.DocumentMapper;
import com.personalenglishai.backend.mapper.WritingExamMetadataMapper;
import com.personalenglishai.backend.mapper.WritingMetadataMapper;
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

    private Document buildDoc(Long id, Long ownerUserId, int latestRevision) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerUserId(ownerUserId);
        doc.setTitle("My Doc");
        doc.setLatestRevision(latestRevision);
        return doc;
    }
}

