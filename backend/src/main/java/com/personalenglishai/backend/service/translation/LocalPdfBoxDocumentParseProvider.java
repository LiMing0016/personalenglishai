package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.stereotype.Component;

@Component
public class LocalPdfBoxDocumentParseProvider implements DocumentParseProvider {
    private final TranslationDocumentParseService parseService;

    public LocalPdfBoxDocumentParseProvider(TranslationDocumentParseService parseService) {
        this.parseService = parseService;
    }

    @Override
    public boolean supports(DocumentParseRequest request) {
        return "PDF".equalsIgnoreCase(request.fileType())
                || TranslationDocumentFileTypes.hasExtension(request.originalFilename(), "pdf")
                || contentTypeContains(request.contentType(), "pdf");
    }

    @Override
    public DocumentParseProviderType providerType() {
        return DocumentParseProviderType.LOCAL_PDFBOX;
    }

    @Override
    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        return parseService.parsePdf(request.originalFilename(), request.bytes());
    }

    private boolean contentTypeContains(String contentType, String expected) {
        return contentType != null && contentType.toLowerCase().contains(expected.toLowerCase());
    }
}
