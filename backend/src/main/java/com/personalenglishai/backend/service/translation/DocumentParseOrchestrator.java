package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentParseOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(DocumentParseOrchestrator.class);

    private final List<DocumentParseProvider> providers;
    private final boolean pdfBoxEnabled;
    private final boolean pdfBoxFallbackEnabled;

    @Autowired
    public DocumentParseOrchestrator(
            List<DocumentParseProvider> providers,
            @Value("${app.document-parse.pdfbox.enabled:false}") boolean pdfBoxEnabled,
            @Value("${app.document-parse.pdfbox.fallback-enabled:false}") boolean pdfBoxFallbackEnabled) {
        this.providers = providers == null ? List.of() : providers;
        this.pdfBoxEnabled = pdfBoxEnabled;
        this.pdfBoxFallbackEnabled = pdfBoxFallbackEnabled;
    }

    public DocumentParseOrchestrator(List<DocumentParseProvider> providers) {
        this(providers, false, false);
    }

    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        long startedAt = System.currentTimeMillis();
        DocumentParseMode parseMode = request.parseMode() == null ? DocumentParseMode.STANDARD : request.parseMode();
        DocumentParseProviderPreference providerPreference = request.providerPreference() == null
                ? DocumentParseProviderPreference.AUTO
                : request.providerPreference();
        List<String> warnings = new ArrayList<>();

        log.info(
                "[document-parse] start file={} fileType={} contentType={} parseMode={} providerPreference={} pdfBoxEnabled={} pdfBoxFallbackEnabled={}",
                request.originalFilename(),
                request.fileType(),
                request.contentType(),
                parseMode.wireName(),
                providerPreference.wireName(),
                pdfBoxEnabled,
                pdfBoxFallbackEnabled
        );

        if (parseMode == DocumentParseMode.HIGH_QUALITY) {
            Optional<DocumentParseProvider> localPaddleVlProvider = providerPreference != DocumentParseProviderPreference.PADDLE_OCR
                    ? findProvider(request, DocumentParseProviderType.LOCAL_PADDLE_VL)
                    : Optional.empty();
            if (localPaddleVlProvider.isPresent()) {
                try {
                    log.info("[document-parse] provider selected provider={} reason=high-quality-local-vl parseMode={}",
                            localPaddleVlProvider.get().providerType().wireName(), parseMode.wireName());
                    return withMetadata(localPaddleVlProvider.get().parse(request), localPaddleVlProvider.get(), parseMode, false, startedAt, warnings);
                } catch (RuntimeException ex) {
                    log.warn("[document-parse] provider failed provider={} parseMode={} elapsedMs={} error={}",
                            localPaddleVlProvider.get().providerType().wireName(),
                            parseMode.wireName(),
                            Math.max(0, System.currentTimeMillis() - startedAt),
                            ex.getMessage());
                    warnings.add("本地 PaddleOCR-VL 解析失败，已尝试使用本地 PaddleOCR 解析。");
                }
            }

            Optional<DocumentParseProvider> baiduPaddleVlProvider = providerPreference == DocumentParseProviderPreference.AUTO
                    ? findProvider(request, DocumentParseProviderType.BAIDU_PADDLE_VL)
                    : Optional.empty();
            if (baiduPaddleVlProvider.isPresent()) {
                try {
                    log.info("[document-parse] provider selected provider={} reason=high-quality-cloud parseMode={}",
                            baiduPaddleVlProvider.get().providerType().wireName(), parseMode.wireName());
                    return withMetadata(baiduPaddleVlProvider.get().parse(request), baiduPaddleVlProvider.get(), parseMode, !warnings.isEmpty(), startedAt, warnings);
                } catch (RuntimeException ex) {
                    log.warn("[document-parse] provider failed provider={} parseMode={} elapsedMs={} error={}",
                            baiduPaddleVlProvider.get().providerType().wireName(),
                            parseMode.wireName(),
                            Math.max(0, System.currentTimeMillis() - startedAt),
                            ex.getMessage());
                    warnings.add("百度 PaddleOCR-VL 解析失败，已尝试使用本地 PaddleOCR 解析。");
                }
            }
        }

        Optional<DocumentParseProvider> paddleProvider = findProvider(request, DocumentParseProviderType.PADDLE_OCR);
        if (paddleProvider.isPresent()) {
            try {
                log.info("[document-parse] provider selected provider={} reason=paddle-first parseMode={}",
                        paddleProvider.get().providerType().wireName(), parseMode.wireName());
                return withMetadata(paddleProvider.get().parse(request), paddleProvider.get(), parseMode, !warnings.isEmpty(), startedAt, warnings);
            } catch (RuntimeException ex) {
                log.warn("[document-parse] provider failed provider={} parseMode={} elapsedMs={} error={}",
                        paddleProvider.get().providerType().wireName(),
                        parseMode.wireName(),
                        Math.max(0, System.currentTimeMillis() - startedAt),
                        ex.getMessage());
                if (!pdfBoxFallbackEnabled) {
                    throw ex;
                }
                warnings.add("PaddleOCR 解析失败，已尝试使用 PDFBox 解析。");
                DocumentParseProvider fallbackProvider = findPdfBoxProvider(request)
                        .orElseThrow(() -> unsupported());
                return withMetadata(fallbackProvider.parse(request), fallbackProvider, parseMode, true, startedAt, warnings);
            }
        }

        if (parseMode == DocumentParseMode.HIGH_QUALITY) {
            Optional<DocumentParseProvider> highQualityProvider = findProvider(request, DocumentParseProviderType.THIRD_PARTY_LAYOUT);
            if (highQualityProvider.isPresent()) {
                try {
                    log.info("[document-parse] provider selected provider={} reason=high-quality parseMode={}",
                            highQualityProvider.get().providerType().wireName(), parseMode.wireName());
                    return withMetadata(highQualityProvider.get().parse(request), highQualityProvider.get(), parseMode, false, startedAt, warnings);
                } catch (RuntimeException ex) {
                    log.warn("[document-parse] provider failed provider={} parseMode={} elapsedMs={} error={}",
                            highQualityProvider.get().providerType().wireName(),
                            parseMode.wireName(),
                            Math.max(0, System.currentTimeMillis() - startedAt),
                            ex.getMessage());
                    warnings.add("高质量解析失败，已尝试使用标准解析。");
                }
            } else {
                warnings.add("高质量解析 Provider 未启用，已使用标准解析。");
            }

            DocumentParseProvider fallbackProvider = findPdfBoxProvider(request)
                    .orElseThrow(() -> unsupported());
            return withMetadata(fallbackProvider.parse(request), fallbackProvider, parseMode, true, startedAt, warnings);
        }

        DocumentParseProvider provider = findPdfBoxProvider(request)
                .or(() -> providers.stream()
                        .filter(candidate -> candidate.providerType() != DocumentParseProviderType.LOCAL_PDFBOX)
                        .filter(candidate -> candidate.supports(request))
                        .findFirst())
                .orElseThrow(() -> unsupported());
        log.info("[document-parse] provider selected provider={} reason=default parseMode={}",
                provider.providerType().wireName(), parseMode.wireName());
        return withMetadata(provider.parse(request), provider, parseMode, false, startedAt, warnings);
    }

    private Optional<DocumentParseProvider> findProvider(DocumentParseRequest request, DocumentParseProviderType providerType) {
        return providers.stream()
                .filter(provider -> provider.providerType() == providerType)
                .filter(provider -> provider.supports(request))
                .findFirst();
    }

    private Optional<DocumentParseProvider> findPdfBoxProvider(DocumentParseRequest request) {
        if (!pdfBoxEnabled) {
            return Optional.empty();
        }
        return findProvider(request, DocumentParseProviderType.LOCAL_PDFBOX);
    }

    private TranslationDocumentParseResponse withMetadata(
            TranslationDocumentParseResponse response,
            DocumentParseProvider provider,
            DocumentParseMode parseMode,
            boolean fallbackUsed,
            long startedAt,
            List<String> warnings) {
        response.setProvider(provider.providerType().wireName());
        response.setParseMode(parseMode.wireName());
        response.setFallbackUsed(fallbackUsed);
        response.setElapsedMs(Math.max(0, System.currentTimeMillis() - startedAt));
        if (!warnings.isEmpty()) {
            List<String> mergedWarnings = new ArrayList<>(warnings);
            mergedWarnings.addAll(response.getWarnings());
            response.setWarnings(mergedWarnings);
        }
        TranslationDocumentParseResponse enriched = TranslationDocumentKnowledgePipeline.enrich(response);
        log.info(
                "[document-parse] success provider={} parseMode={} fallbackUsed={} status={} ocrStatus={} pages={} blocks={} elapsedMs={}",
                provider.providerType().wireName(),
                parseMode.wireName(),
                fallbackUsed,
                enriched.getParseStatus(),
                enriched.getOcrStatus(),
                enriched.getPageCount(),
                enriched.getBlockCount(),
                enriched.getElapsedMs()
        );
        return enriched;
    }

    private BizException unsupported() {
        return new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "暂不支持该文件解析模式，请更换文件或解析方式");
    }
}
