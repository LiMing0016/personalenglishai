package com.personalenglishai.backend.service.translation;

public record ExportedTranslationDocumentFile(
        String fileName,
        String contentType,
        byte[] bytes
) {
}
