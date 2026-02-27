package com.personalenglishai.backend.ai.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Optional context references.
 */
public class ContextRefs {

    private String docId;

    private Integer revision;

    @Valid
    private SelectionRange selectionRange;

    private List<String> pinnedIds;
    private String docVersion;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public SelectionRange getSelectionRange() {
        return selectionRange;
    }

    public void setSelectionRange(SelectionRange selectionRange) {
        this.selectionRange = selectionRange;
    }

    public List<String> getPinnedIds() {
        return pinnedIds;
    }

    public void setPinnedIds(List<String> pinnedIds) {
        this.pinnedIds = pinnedIds;
    }

    public String getDocVersion() {
        return docVersion;
    }

    public void setDocVersion(String docVersion) {
        this.docVersion = docVersion;
    }
}
