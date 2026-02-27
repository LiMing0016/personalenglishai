package com.personalenglishai.backend.ai.context;

/**
 * AI context built from request and document data.
 */
public class AIContext {

    private final boolean failed;
    private final String errorContent;
    private final String draftContent;
    private final String docId;
    private final boolean docFound;

    private AIContext(boolean failed, String errorContent, String draftContent, String docId, boolean docFound) {
        this.failed = failed;
        this.errorContent = errorContent;
        this.draftContent = draftContent;
        this.docId = docId;
        this.docFound = docFound;
    }

    public static AIContext failed(String errorContent, String docId, boolean docFound) {
        return new AIContext(true, errorContent, null, docId, docFound);
    }

    public static AIContext success(String draftContent, String docId, boolean docFound) {
        return new AIContext(false, null, draftContent, docId, docFound);
    }

    public boolean isFailed() {
        return failed;
    }

    public String getErrorContent() {
        return errorContent;
    }

    public String getDraftContent() {
        return draftContent;
    }

    public String getDocId() {
        return docId;
    }

    public boolean isDocFound() {
        return docFound;
    }
}
