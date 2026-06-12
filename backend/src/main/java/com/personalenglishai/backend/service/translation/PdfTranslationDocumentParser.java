package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.stereotype.Component;

@Component
public class PdfTranslationDocumentParser implements TranslationDocumentParser {
    private final TranslationDocumentParseService parseService;

    public PdfTranslationDocumentParser(TranslationDocumentParseService parseService) {
        this.parseService = parseService;
    }

    @Override
    public boolean supports(UploadedTranslationDocument document) {
        return TranslationDocumentFileTypes.hasExtension(document, "pdf")
                || TranslationDocumentFileTypes.contentTypeContains(document, "pdf");
    }

    @Override
    public TranslationDocumentParseResponse parse(UploadedTranslationDocument document) {
        return parseService.parsePdf(document.getOriginalFilename(), document.getBytes());
    }
}
