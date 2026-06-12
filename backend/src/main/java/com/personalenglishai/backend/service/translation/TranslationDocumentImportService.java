package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslationDocumentImportService {
    private final List<TranslationDocumentParser> parsers;
    private final DocumentParseOrchestrator parseOrchestrator;

    public TranslationDocumentImportService(List<TranslationDocumentParser> parsers) {
        this(parsers, null);
    }

    @Autowired
    public TranslationDocumentImportService(
            List<TranslationDocumentParser> parsers,
            DocumentParseOrchestrator parseOrchestrator) {
        this.parsers = parsers;
        this.parseOrchestrator = parseOrchestrator;
    }

    public TranslationDocumentParseResponse importDocument(UploadedTranslationDocument document) {
        validate(document);
        if (TranslationDocumentFileTypes.hasExtension(document, "doc")) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "暂不支持 DOC，请转为 DOCX 后上传");
        }

        if (isPdf(document) && parseOrchestrator != null) {
            return parseOrchestrator.parse(new DocumentParseRequest(
                    document.getOriginalFilename(),
                    document.getContentType(),
                    document.getBytes(),
                    "PDF",
                    document.getParseMode(),
                    document.getMode()
            ));
        }

        return parsers.stream()
                .filter(parser -> parser.supports(document))
                .findFirst()
                .map(parser -> parser.parse(document))
                .orElseThrow(() -> new BizException(
                        ErrorCode.COMMON_VALIDATION_ERROR,
                        "暂不支持该文件格式，请上传 PDF、DOCX、TXT 或 MD"
                ));
    }

    private void validate(UploadedTranslationDocument document) {
        if (document == null || document.getBytes() == null || document.getBytes().length == 0) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "上传文件不能为空");
        }
    }

    private boolean isPdf(UploadedTranslationDocument document) {
        return TranslationDocumentFileTypes.hasExtension(document, "pdf")
                || TranslationDocumentFileTypes.contentTypeContains(document, "pdf");
    }
}
