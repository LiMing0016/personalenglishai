package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;

public interface DocumentParseProvider {
    boolean supports(DocumentParseRequest request);

    DocumentParseProviderType providerType();

    TranslationDocumentParseResponse parse(DocumentParseRequest request);
}
