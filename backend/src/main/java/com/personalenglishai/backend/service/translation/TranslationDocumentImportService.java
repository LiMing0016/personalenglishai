package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class TranslationDocumentImportService {
    private final List<TranslationDocumentParser> parsers;
    private final DocumentParseOrchestrator parseOrchestrator;
    private final TranslationDocumentKnowledgeStore knowledgeStore;

    public TranslationDocumentImportService(List<TranslationDocumentParser> parsers) {
        this(parsers, null, null);
    }

    public TranslationDocumentImportService(
            List<TranslationDocumentParser> parsers,
            DocumentParseOrchestrator parseOrchestrator) {
        this(parsers, parseOrchestrator, null);
    }

    @Autowired
    public TranslationDocumentImportService(
            List<TranslationDocumentParser> parsers,
            DocumentParseOrchestrator parseOrchestrator,
            TranslationDocumentKnowledgeStore knowledgeStore) {
        this.parsers = parsers;
        this.parseOrchestrator = parseOrchestrator;
        this.knowledgeStore = knowledgeStore;
    }

    public TranslationDocumentParseResponse importDocument(UploadedTranslationDocument document) {
        validate(document);
        String documentId = stableDocumentId(document);
        if (TranslationDocumentFileTypes.hasExtension(document, "doc")) {
            String message = "暂不支持 DOC，请转为 DOCX 后上传";
            persist(failedResponse(document, documentId, "DOC", message));
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, message);
        }

        try {
            if (isPdf(document) && parseOrchestrator != null) {
                TranslationDocumentParseResponse response = parseOrchestrator.parse(new DocumentParseRequest(
                        document.getOriginalFilename(),
                        document.getContentType(),
                        document.getBytes(),
                        "PDF",
                        document.getParseMode(),
                        document.getMode()
                ));
                response.setDocumentId(documentId);
                return persist(TranslationDocumentKnowledgePipeline.enrich(response));
            }

            TranslationDocumentParser parser = parsers.stream()
                    .filter(candidate -> candidate.supports(document))
                    .findFirst()
                    .orElse(null);
            if (parser == null) {
                String message = "暂不支持该文件格式，请上传 PDF、DOCX、TXT 或 MD";
                persist(failedResponse(document, documentId, inferSourceType(document), message));
                throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, message);
            }
            TranslationDocumentParseResponse response = parser.parse(document);
            response.setDocumentId(documentId);
            return persist(TranslationDocumentKnowledgePipeline.enrich(response));
        } catch (BizException e) {
            if (!isUnsupportedDocumentException(e)) {
                persist(failedResponse(document, documentId, inferSourceType(document), e.getMessage()));
            }
            throw e;
        }
    }

    private TranslationDocumentParseResponse persist(TranslationDocumentParseResponse response) {
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

    private TranslationDocumentParseResponse failedResponse(
            UploadedTranslationDocument document,
            String documentId,
            String sourceType,
            String message) {
        TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
        response.setDocumentId(documentId);
        response.setFileName(document.getOriginalFilename());
        response.setSourceType(sourceType == null || sourceType.isBlank() ? "UNKNOWN" : sourceType);
        response.setParseStatus("FAILED");
        response.setOcrStatus("NOT_REQUIRED");
        response.setProvider("import-validator");
        response.setParseMode(document.getParseMode().wireName());
        response.setWarnings(List.of(message == null || message.isBlank() ? "文档解析失败" : message));
        return TranslationDocumentKnowledgePipeline.enrich(response);
    }

    private boolean isUnsupportedDocumentException(BizException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("暂不支持 DOC") || message.contains("暂不支持该文件格式"));
    }

    private String inferSourceType(UploadedTranslationDocument document) {
        if (isPdf(document)) {
            return "PDF";
        }
        if (TranslationDocumentFileTypes.hasExtension(document, "doc")) {
            return "DOC";
        }
        if (TranslationDocumentFileTypes.hasExtension(document, "docx")
                || TranslationDocumentFileTypes.contentTypeContains(document, "wordprocessingml.document")) {
            return "DOCX";
        }
        if (TranslationDocumentFileTypes.hasExtension(document, "md")
                || TranslationDocumentFileTypes.contentTypeContains(document, "markdown")) {
            return "MD";
        }
        if (TranslationDocumentFileTypes.hasExtension(document, "txt")
                || TranslationDocumentFileTypes.contentTypeContains(document, "text/plain")) {
            return "TXT";
        }
        return "UNKNOWN";
    }

    private String stableDocumentId(UploadedTranslationDocument document) {
        String fileName = document.getOriginalFilename() == null ? "" : document.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        byte[] bytes = document.getBytes() == null ? new byte[0] : document.getBytes();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(fileName.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(bytes);
            return "translation-" + HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
