package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "paddle")
public class PaddleDocumentParseProvider implements DocumentParseProvider {
    private final TranslationDocumentParseService parseService;

    @Autowired
    public PaddleDocumentParseProvider(TranslationDocumentParseService parseService) {
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
        return DocumentParseProviderType.PADDLE_OCR;
    }

    @Override
    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        TranslationDocumentParseResponse response = parseService.parsePdfWithOcr(
                request.originalFilename(),
                request.bytes(),
                TranslationOcrOptions.fromRequest(request)
        );
        if (!"SUCCEEDED".equals(response.getParseStatus())) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, firstWarning(response));
        }
        return response;
    }

    private String firstWarning(TranslationDocumentParseResponse response) {
        if (response != null && response.getWarnings() != null) {
            return response.getWarnings().stream()
                    .filter(warning -> warning != null && !warning.isBlank())
                    .findFirst()
                    .orElse("PaddleOCR 解析失败");
        }
        return "PaddleOCR 解析失败";
    }

    private boolean contentTypeContains(String contentType, String expected) {
        return contentType != null && contentType.toLowerCase().contains(expected.toLowerCase());
    }
}
