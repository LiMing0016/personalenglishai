package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;

public interface TranslationDocumentParser {
    boolean supports(UploadedTranslationDocument document);

    TranslationDocumentParseResponse parse(UploadedTranslationDocument document);
}
