package com.personalenglishai.backend.dto.translation;

import java.util.ArrayList;
import java.util.List;

public class TranslationDocumentWorkspaceStateDto {
    private List<TranslationDocumentUserBookmarkDto> userBookmarks = new ArrayList<>();
    private List<TranslationDocumentStudyNoteDto> studyNotes = new ArrayList<>();
    private List<String> collapsedOutlineItemIds = new ArrayList<>();
    private Integer currentPage;
    private String activeBlockId;
    private String activeOutlineItemId;
    private String activeNoteId;
    private String updatedAt;

    public List<TranslationDocumentUserBookmarkDto> getUserBookmarks() {
        return userBookmarks;
    }

    public void setUserBookmarks(List<TranslationDocumentUserBookmarkDto> userBookmarks) {
        this.userBookmarks = userBookmarks == null ? new ArrayList<>() : userBookmarks;
    }

    public List<TranslationDocumentStudyNoteDto> getStudyNotes() {
        return studyNotes;
    }

    public void setStudyNotes(List<TranslationDocumentStudyNoteDto> studyNotes) {
        this.studyNotes = studyNotes == null ? new ArrayList<>() : studyNotes;
    }

    public List<String> getCollapsedOutlineItemIds() {
        return collapsedOutlineItemIds;
    }

    public void setCollapsedOutlineItemIds(List<String> collapsedOutlineItemIds) {
        this.collapsedOutlineItemIds = collapsedOutlineItemIds == null ? new ArrayList<>() : collapsedOutlineItemIds;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public String getActiveBlockId() {
        return activeBlockId;
    }

    public void setActiveBlockId(String activeBlockId) {
        this.activeBlockId = activeBlockId;
    }

    public String getActiveOutlineItemId() {
        return activeOutlineItemId;
    }

    public void setActiveOutlineItemId(String activeOutlineItemId) {
        this.activeOutlineItemId = activeOutlineItemId;
    }

    public String getActiveNoteId() {
        return activeNoteId;
    }

    public void setActiveNoteId(String activeNoteId) {
        this.activeNoteId = activeNoteId;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
