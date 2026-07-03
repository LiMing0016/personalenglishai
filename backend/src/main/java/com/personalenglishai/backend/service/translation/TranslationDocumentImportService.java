package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentAssetDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentElementDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentOutlineItemDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;

@Service
public class TranslationDocumentImportService {
    private final List<TranslationDocumentParser> parsers;
    private final DocumentParseOrchestrator parseOrchestrator;
    private final TranslationDocumentKnowledgeStore knowledgeStore;
    private final TranslationDocumentFileStorage fileStorage;
    private final Executor backgroundParseExecutor;
    private final int initialOcrPages;
    private final int backgroundChunkPages;

    public TranslationDocumentImportService(List<TranslationDocumentParser> parsers) {
        this(parsers, null, null, null, Runnable::run, 10, 10);
    }

    @Autowired
    public TranslationDocumentImportService(
            List<TranslationDocumentParser> parsers,
            DocumentParseOrchestrator parseOrchestrator,
            TranslationDocumentKnowledgeStore knowledgeStore,
            TranslationDocumentFileStorage fileStorage,
            @Qualifier("translationDocumentParseExecutor") Executor backgroundParseExecutor,
            @Value("${app.translation.document-import.initial-ocr-pages:10}") int initialOcrPages,
            @Value("${app.translation.document-import.background-chunk-pages:10}") int backgroundChunkPages) {
        this.parsers = parsers;
        this.parseOrchestrator = parseOrchestrator;
        this.knowledgeStore = knowledgeStore;
        this.fileStorage = fileStorage;
        this.backgroundParseExecutor = backgroundParseExecutor;
        this.initialOcrPages = Math.max(1, Math.min(50, initialOcrPages));
        this.backgroundChunkPages = Math.max(1, Math.min(50, backgroundChunkPages));
    }

    public TranslationDocumentImportService(
            List<TranslationDocumentParser> parsers,
            DocumentParseOrchestrator parseOrchestrator,
            TranslationDocumentKnowledgeStore knowledgeStore,
            TranslationDocumentFileStorage fileStorage) {
        this(parsers, parseOrchestrator, knowledgeStore, fileStorage, Runnable::run, 10, 10);
    }

    public TranslationDocumentParseResponse importDocument(UploadedTranslationDocument document) {
        validate(document);
        if (TranslationDocumentFileTypes.hasExtension(document, "doc")) {
            throw new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "暂不支持 DOC，请转为 DOCX 后上传");
        }

        if (isPdf(document) && parseOrchestrator != null) {
            int totalPages = countPdfPages(document.getBytes());
            TranslationDocumentParseResponse response = parseOrchestrator.parse(new DocumentParseRequest(
                    document.getOriginalFilename(),
                    document.getContentType(),
                    document.getBytes(),
                    "PDF",
                    document.getParseMode(),
                    document.getMode(),
                    document.getProviderPreference(),
                    1,
                    null,
                    initialOcrPages
            ));
            TranslationDocumentParseResponse initialResponse = markInitialOcrResponse(response, totalPages);
            TranslationDocumentParseResponse imported = completeImport(document, TranslationDocumentKnowledgePipeline.enrich(initialResponse));
            scheduleBackgroundOcr(document, imported, totalPages);
            return imported;
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

    private TranslationDocumentParseResponse markInitialOcrResponse(
            TranslationDocumentParseResponse response,
            int totalPages) {
        if (response == null) {
            return null;
        }
        int knownPageCount = Math.max(response.getPageCount(), totalPages);
        response.setPageCount(knownPageCount);
        if (knownPageCount > initialOcrPages) {
            response.setOcrStatus("PARTIAL");
            response.setWarnings(mergeWarnings(
                    response.getWarnings(),
                    "已完成前 " + initialOcrPages + " 页本地 OCR 解析，剩余页面正在后台继续解析。"
            ));
        }
        return response;
    }

    private void scheduleBackgroundOcr(
            UploadedTranslationDocument document,
            TranslationDocumentParseResponse initialResponse,
            int totalPages) {
        if (document == null
                || initialResponse == null
                || backgroundParseExecutor == null
                || knowledgeStore == null
                || parseOrchestrator == null
                || isBlank(initialResponse.getDocumentId())
                || totalPages <= initialOcrPages) {
            return;
        }
        backgroundParseExecutor.execute(() -> runBackgroundOcr(document, initialResponse, totalPages));
    }

    private void runBackgroundOcr(
            UploadedTranslationDocument document,
            TranslationDocumentParseResponse initialResponse,
            int totalPages) {
        TranslationDocumentParseResponse accumulated = initialResponse;
        for (int pageStart = initialOcrPages + 1; pageStart <= totalPages; pageStart += backgroundChunkPages) {
            int pageEnd = Math.min(totalPages, pageStart + backgroundChunkPages - 1);
            try {
                TranslationDocumentParseResponse next = parseOrchestrator.parse(new DocumentParseRequest(
                        document.getOriginalFilename(),
                        document.getContentType(),
                        document.getBytes(),
                        "PDF",
                        document.getParseMode(),
                        document.getMode(),
                        document.getProviderPreference(),
                        pageStart,
                        pageEnd,
                        backgroundChunkPages
                ));
                accumulated = mergeOcrResponses(accumulated, next, totalPages, pageEnd >= totalPages);
                knowledgeStore.save(accumulated);
            } catch (RuntimeException ex) {
                accumulated.setOcrStatus("PARTIAL");
                accumulated.setWarnings(mergeWarnings(
                        accumulated.getWarnings(),
                        "后台 OCR 解析在第 " + pageStart + "-" + pageEnd + " 页失败：" + ex.getMessage()
                ));
                knowledgeStore.save(accumulated);
                return;
            }
        }
    }

    private TranslationDocumentParseResponse mergeOcrResponses(
            TranslationDocumentParseResponse accumulated,
            TranslationDocumentParseResponse next,
            int totalPages,
            boolean complete) {
        TranslationDocumentParseResponse merged = new TranslationDocumentParseResponse();
        merged.setDocumentId(accumulated.getDocumentId());
        merged.setFileName(accumulated.getFileName());
        merged.setSourceType(accumulated.getSourceType());
        merged.setParseStatus("SUCCEEDED");
        merged.setOcrStatus(complete ? "SUCCEEDED" : "PARTIAL");
        merged.setProvider(accumulated.getProvider());
        merged.setParseMode(accumulated.getParseMode());
        merged.setFallbackUsed(accumulated.isFallbackUsed());
        merged.setFileUrl(accumulated.getFileUrl());
        merged.setFilePersisted(accumulated.isFilePersisted());
        merged.setStorageProvider(accumulated.getStorageProvider());
        merged.setElapsedMs(accumulated.getElapsedMs() + next.getElapsedMs());
        merged.setPageCount(totalPages);
        merged.setBlocks(concat(accumulated.getBlocks(), next.getBlocks()));
        merged.setElements(concat(accumulated.getElements(), next.getElements()));
        merged.setOutline(concat(accumulated.getOutline(), next.getOutline()));
        merged.setAssets(concat(accumulated.getAssets(), next.getAssets()));
        merged.setWarnings(mergeWarnings(
                concat(accumulated.getWarnings(), next.getWarnings()),
                complete
                        ? "本地 OCR 已完成全部页面解析。"
                        : "本地 OCR 正在后台继续解析剩余页面。"
        ));
        return TranslationDocumentKnowledgePipeline.enrich(merged);
    }

    private int countPdfPages(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            return 0;
        }
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

    private <T> List<T> concat(List<T> first, List<T> second) {
        List<T> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return merged;
    }

    private List<String> mergeWarnings(List<String> warnings, String extraWarning) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (warnings != null) {
            warnings.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(merged::add);
        }
        if (!isBlank(extraWarning)) {
            merged.add(extraWarning);
        }
        return new ArrayList<>(merged);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
