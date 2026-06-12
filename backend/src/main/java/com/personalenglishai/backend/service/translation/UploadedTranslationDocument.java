package com.personalenglishai.backend.service.translation;

public class UploadedTranslationDocument {
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;
    private final String mode;
    private final DocumentParseMode parseMode;

    public UploadedTranslationDocument(String originalFilename, String contentType, byte[] bytes, String mode) {
        this(originalFilename, contentType, bytes, mode, DocumentParseMode.STANDARD);
    }

    public UploadedTranslationDocument(String originalFilename, String contentType, byte[] bytes, String mode, DocumentParseMode parseMode) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.bytes = bytes;
        this.mode = mode;
        this.parseMode = parseMode == null ? DocumentParseMode.STANDARD : parseMode;
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
}
