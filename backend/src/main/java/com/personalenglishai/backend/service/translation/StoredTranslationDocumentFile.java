package com.personalenglishai.backend.service.translation;

import java.nio.file.Path;

public class StoredTranslationDocumentFile {
    private final String documentId;
    private final String fileName;
    private final String contentType;
    private final long fileSize;
    private final String sha256;
    private final String storageProvider;
    private final String storageKey;
    private final String fileUrl;
    private final Path path;

    public StoredTranslationDocumentFile(
            String documentId,
            String fileName,
            String contentType,
            long fileSize,
            String sha256,
            String storageProvider,
            String storageKey,
            String fileUrl,
            Path path) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.fileUrl = fileUrl;
        this.path = path;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public Path getPath() {
        return path;
    }
}
