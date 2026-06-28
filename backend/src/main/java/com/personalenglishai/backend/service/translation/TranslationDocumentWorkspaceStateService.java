package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import com.personalenglishai.backend.dto.translation.TranslationDocumentStudyNoteDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentUserBookmarkDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentWorkspaceStateDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TranslationDocumentWorkspaceStateService {
    private final TranslationDocumentKnowledgeStore knowledgeStore;

    public TranslationDocumentWorkspaceStateService(TranslationDocumentKnowledgeStore knowledgeStore) {
        this.knowledgeStore = knowledgeStore;
    }

    @Transactional
    public TranslationDocumentWorkspaceStateDto save(String documentId, TranslationDocumentWorkspaceStateDto state) {
        TranslationDocumentParseResponse response = knowledgeStore.findByDocumentId(documentId)
                .orElseThrow(() -> new BizException(ErrorCode.DOC_NOT_FOUND, "翻译文档知识快照不存在"));
        TranslationDocumentWorkspaceStateDto normalized = normalize(documentId, state);
        response.setWorkspaceState(normalized);
        knowledgeStore.save(response);
        return normalized;
    }

    private TranslationDocumentWorkspaceStateDto normalize(String documentId, TranslationDocumentWorkspaceStateDto state) {
        TranslationDocumentWorkspaceStateDto normalized = state == null ? new TranslationDocumentWorkspaceStateDto() : state;
        if (normalized.getCurrentPage() != null && normalized.getCurrentPage() < 1) {
            normalized.setCurrentPage(1);
        }
        normalized.setCollapsedOutlineItemIds(normalized.getCollapsedOutlineItemIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList());
        normalized.setUserBookmarks(normalized.getUserBookmarks().stream()
                .filter(bookmark -> bookmark != null && !isBlank(bookmark.getId()) && !isBlank(bookmark.getTitle()))
                .peek(this::normalizeBookmark)
                .toList());
        normalized.setStudyNotes(normalized.getStudyNotes().stream()
                .filter(note -> note != null && !isBlank(note.getId()) && !isBlank(note.getTitle()))
                .peek(note -> normalizeNote(documentId, note))
                .toList());
        return normalized;
    }

    private void normalizeBookmark(TranslationDocumentUserBookmarkDto bookmark) {
        if (bookmark.getPageNumber() < 1) {
            bookmark.setPageNumber(1);
        }
        if (bookmark.getLevel() < 1) {
            bookmark.setLevel(2);
        }
        if (isBlank(bookmark.getSource())) {
            bookmark.setSource("user_bookmark");
        }
    }

    private void normalizeNote(String documentId, TranslationDocumentStudyNoteDto note) {
        if (isBlank(note.getDocumentId())) {
            note.setDocumentId(documentId);
        }
        if (note.getPageNumber() < 1) {
            note.setPageNumber(1);
        }
        if (isBlank(note.getSource())) {
            note.setSource("manual");
        }
        if (isBlank(note.getStatus())) {
            note.setStatus("saved");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
