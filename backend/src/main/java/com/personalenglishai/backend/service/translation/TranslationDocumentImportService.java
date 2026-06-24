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
    private final TranslationDocumentKnowledgeStore knowledgeStore;
    private final TranslationDocumentFileStorage fileStorage;

    public TranslationDocumentImportService(List<TranslationDocumentParser> parsers) {
        this(parsers, null, null, null);
    }

    @Autowired
    public TranslationDocumentImportService(
            List<TranslationDocumentParser> parsers,
            DocumentParseOrchestrator parseOrchestrator,
            TranslationDocumentKnowledgeStore knowledgeStore,
            TranslationDocumentFileStorage fileStorage) {
        this.parsers = parsers;
        this.parseOrchestrator = parseOrchestrator;
        this.knowledgeStore = knowledgeStore;
        this.fileStorage = fileStorage;
    }

    public TranslationDocumentParseResponse importDocument(UploadedTranslationDocument document) {
        validate(document);
        if (TranslationDocumentFileTypes.hasExtension(document, "doc")) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "暂不支持 DOC，请转为 DOCX 后上传");
        }

        if (isPdf(document) && parseOrchestrator != null) {
            TranslationDocumentParseResponse response = parseOrchestrator.parse(new DocumentParseRequest(
                    document.getOriginalFilename(),
                    document.getContentType(),
                    document.getBytes(),
                    "PDF",
                    document.getParseMode(),
                    document.getMode()
            ));
            return completeImport(document, TranslationDocumentKnowledgePipeline.enrich(response));
        }

        TranslationDocumentParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(document))
                .findFirst()
                .orElseThrow(() -> new BizException(
                        ErrorCode.COMMON_VALIDATION_ERROR,
                        "暂不支持该文件格式，请上传 PDF、DOCX、TXT 或 MD"
                ));
        return completeImport(document, TranslationDocumentKnowledgePipeline.enrich(parser.parse(document)));
    }

    private TranslationDocumentParseResponse completeImport(
            UploadedTranslationDocument document,
            TranslationDocumentParseResponse response) {
        if (response == null) {
            return null;
        }
        if (isPdf(document) && fileStorage != null && !isBlank(response.getDocumentId())) {
            StoredTranslationDocumentFile storedFile = fileStorage.save(response.getDocumentId(), document);
            response.setFileUrl(storedFile.getFileUrl());
            response.setFilePersisted(true);
            response.setStorageProvider(storedFile.getStorageProvider());
        }
        if (knowledgeStore != null) {
            knowledgeStore.save(response);
        }
        return response;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
