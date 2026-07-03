package com.personalenglishai.backend.service.translation;

@FunctionalInterface
public interface TranslationOcrService {
    TranslationOcrResult recognizePdf(byte[] pdfBytes);

    default TranslationOcrResult recognizePdf(byte[] pdfBytes, DocumentParseMode parseMode) {
        return recognizePdf(pdfBytes);
    }

    default TranslationOcrResult recognizePdf(byte[] pdfBytes, TranslationOcrOptions options) {
        return recognizePdf(pdfBytes, options == null ? DocumentParseMode.STANDARD : options.effectiveParseMode());
    }
}
