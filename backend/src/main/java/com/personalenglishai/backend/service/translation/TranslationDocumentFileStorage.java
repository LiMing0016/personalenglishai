package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.entity.translation.TranslationDocumentFileRecord;
import com.personalenglishai.backend.mapper.translation.TranslationDocumentFileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class TranslationDocumentFileStorage {
    private static final String STORAGE_PROVIDER = "local";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String DEFAULT_FILE_NAME = "document.pdf";

    private final Path storageRoot;
    private final TranslationDocumentFileMapper mapper;

    @Autowired
    public TranslationDocumentFileStorage(
            @Value("${app.translation.document-storage-dir:data/uploads/translation-documents}") String storageRoot,
            TranslationDocumentFileMapper mapper) {
        this(Path.of(storageRoot), mapper);
    }

    TranslationDocumentFileStorage(Path storageRoot, TranslationDocumentFileMapper mapper) {
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public StoredTranslationDocumentFile save(String documentId, UploadedTranslationDocument document) {
        if (isBlank(documentId) || document == null || document.getBytes() == null || document.getBytes().length == 0) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "PDF 文件保存参数无效");
        }

        byte[] bytes = document.getBytes();
        String fileName = normalizeFileName(document.getOriginalFilename());
        String sha256 = sha256(bytes);
        String storageKey = documentId + "/" + sha256.substring(0, 16) + "-" + fileName;
        Path targetPath = resolveStorageKey(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
        } catch (IOException e) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "保存 PDF 原文件失败");
        }

        TranslationDocumentFileRecord record = new TranslationDocumentFileRecord();
        record.setDocumentId(documentId);
        record.setFileName(fileName);
        record.setContentType(normalizeContentType(document.getContentType()));
        record.setFileSize((long) bytes.length);
        record.setSha256(sha256);
        record.setStorageProvider(STORAGE_PROVIDER);
        record.setStorageKey(storageKey);
        mapper.upsert(record);
        return toStored(record);
    }

    public Optional<StoredTranslationDocumentFile> findByDocumentId(String documentId) {
        if (isBlank(documentId)) {
            return Optional.empty();
        }
        TranslationDocumentFileRecord record = mapper.findByDocumentId(documentId);
        if (record == null || isBlank(record.getStorageKey())) {
            return Optional.empty();
        }
        return Optional.of(toStored(record));
    }

    private StoredTranslationDocumentFile toStored(TranslationDocumentFileRecord record) {
        return new StoredTranslationDocumentFile(
                record.getDocumentId(),
                record.getFileName(),
                normalizeContentType(record.getContentType()),
                record.getFileSize() == null ? 0 : record.getFileSize(),
                record.getSha256(),
                record.getStorageProvider() == null ? STORAGE_PROVIDER : record.getStorageProvider(),
                record.getStorageKey(),
                fileUrl(record.getDocumentId()),
                resolveStorageKey(record.getStorageKey())
        );
    }

    private Path resolveStorageKey(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "PDF 文件存储路径无效");
        }
        return resolved;
    }

    private String fileUrl(String documentId) {
        return "/api/translation/documents/" + documentId + "/file";
    }

    private String normalizeFileName(String fileName) {
        if (isBlank(fileName)) {
            return DEFAULT_FILE_NAME;
        }
        String normalizedSeparators = fileName.replace('\\', '/');
        int slashIndex = normalizedSeparators.lastIndexOf('/');
        String nameOnly = slashIndex >= 0 ? normalizedSeparators.substring(slashIndex + 1) : normalizedSeparators;
        String normalized = nameOnly.replaceAll("[\\\\/:*?\"<>|]+", "_").strip();
        return normalized.isBlank() ? DEFAULT_FILE_NAME : normalized;
    }

    private String normalizeContentType(String contentType) {
        return isBlank(contentType) ? DEFAULT_CONTENT_TYPE : contentType;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
