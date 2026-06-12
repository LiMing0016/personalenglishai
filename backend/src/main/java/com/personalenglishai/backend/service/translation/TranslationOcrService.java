package com.personalenglishai.backend.service.translation;

@FunctionalInterface
public interface TranslationOcrService {
    TranslationOcrResult recognizePdf(byte[] pdfBytes);
}
