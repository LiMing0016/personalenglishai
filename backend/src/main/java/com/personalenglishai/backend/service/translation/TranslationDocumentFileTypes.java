package com.personalenglishai.backend.service.translation;

import java.util.Locale;

final class TranslationDocumentFileTypes {
    private TranslationDocumentFileTypes() {
    }

    static String extension(UploadedTranslationDocument document) {
        String filename = document.getOriginalFilename() == null ? "" : document.getOriginalFilename();
        return extension(filename);
    }

    static String extension(String filename) {
        filename = filename == null ? "" : filename;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static boolean hasExtension(UploadedTranslationDocument document, String extension) {
        return extension(document).equals(extension);
    }

    static boolean hasExtension(String filename, String extension) {
        return extension(filename).equals(extension);
    }

    static boolean contentTypeContains(UploadedTranslationDocument document, String token) {
        String contentType = document.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }
}
