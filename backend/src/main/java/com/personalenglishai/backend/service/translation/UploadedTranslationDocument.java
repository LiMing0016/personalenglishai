package com.personalenglishai.backend.service.translation;

public class UploadedTranslationDocument {
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;
    private final String mode;
    private final DocumentParseMode parseMode;
    private final DocumentParseProviderPreference providerPreference;

    public UploadedTranslationDocument(String originalFilename, String contentType, byte[] bytes, String mode) {
        this(originalFilename, contentType, bytes, mode, DocumentParseMode.STANDARD, DocumentParseProviderPreference.AUTO);
    }

    public UploadedTranslationDocument(String originalFilename, String contentType, byte[] bytes, String mode, DocumentParseMode parseMode) {
        this(originalFilename, contentType, bytes, mode, parseMode, DocumentParseProviderPreference.AUTO);
    }

    public UploadedTranslationDocument(
            String originalFilename,
            String contentType,
            byte[] bytes,
            String mode,
            DocumentParseMode parseMode,
            DocumentParseProviderPreference providerPreference) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.bytes = bytes;
        this.mode = mode;
        this.parseMode = parseMode == null ? DocumentParseMode.STANDARD : parseMode;
        this.providerPreference = providerPreference == null ? DocumentParseProviderPreference.AUTO : providerPreference;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getMode() {
        return mode;
    }

    public DocumentParseMode getParseMode() {
        return parseMode;
    }

    public DocumentParseProviderPreference getProviderPreference() {
        return providerPreference;
    }
}
