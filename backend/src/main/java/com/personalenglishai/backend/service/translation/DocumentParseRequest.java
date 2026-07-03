package com.personalenglishai.backend.service.translation;

public record DocumentParseRequest(
        String originalFilename,
        String contentType,
        byte[] bytes,
        String fileType,
        DocumentParseMode parseMode,
        String agentMode,
        DocumentParseProviderPreference providerPreference,
        Integer pageStart,
        Integer pageEnd,
        Integer maxPages
) {
    public DocumentParseRequest(
            String originalFilename,
            String contentType,
            byte[] bytes,
            String fileType,
            DocumentParseMode parseMode,
            String agentMode,
            DocumentParseProviderPreference providerPreference) {
        this(
                originalFilename,
                contentType,
                bytes,
                fileType,
                parseMode,
                agentMode,
                providerPreference,
                null,
                null,
                null
        );
    }

    public DocumentParseRequest(
            String originalFilename,
            String contentType,
            byte[] bytes,
            String fileType,
            DocumentParseMode parseMode,
            String agentMode) {
        this(
                originalFilename,
                contentType,
                bytes,
                fileType,
                parseMode,
                agentMode,
                DocumentParseProviderPreference.AUTO,
                null,
                null,
                null
        );
    }

    public DocumentParseRequest {
        if (providerPreference == null) {
            providerPreference = DocumentParseProviderPreference.AUTO;
        }
        if (pageStart != null && pageStart < 1) {
            pageStart = 1;
        }
        if (pageEnd != null && pageEnd < 1) {
            pageEnd = null;
        }
        if (maxPages != null && maxPages < 1) {
            maxPages = 1;
        }
    }
}
