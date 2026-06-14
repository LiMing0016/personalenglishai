package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentParseOrchestrator {
    private final List<DocumentParseProvider> providers;

    public DocumentParseOrchestrator(List<DocumentParseProvider> providers) {
        this.providers = providers == null ? List.of() : providers;
    }

    public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
        long startedAt = System.currentTimeMillis();
        DocumentParseMode parseMode = request.parseMode() == null ? DocumentParseMode.STANDARD : request.parseMode();
        List<String> warnings = new ArrayList<>();

        if (parseMode == DocumentParseMode.HIGH_QUALITY) {
            Optional<DocumentParseProvider> highQualityProvider = findProvider(request, DocumentParseProviderType.THIRD_PARTY_LAYOUT);
            if (highQualityProvider.isPresent()) {
                try {
                    return withMetadata(highQualityProvider.get().parse(request), highQualityProvider.get(), parseMode, false, startedAt, warnings);
                } catch (RuntimeException ex) {
                    warnings.add("高质量解析失败，已尝试使用标准解析。");
                }
            } else {
                warnings.add("高质量解析 Provider 未启用，已使用标准解析。");
            }

            DocumentParseProvider fallbackProvider = findProvider(request, DocumentParseProviderType.LOCAL_PDFBOX)
                    .orElseThrow(() -> unsupported());
            return withMetadata(fallbackProvider.parse(request), fallbackProvider, parseMode, true, startedAt, warnings);
        }

        DocumentParseProvider provider = findProvider(request, DocumentParseProviderType.LOCAL_PDFBOX)
                .or(() -> providers.stream().filter(candidate -> candidate.supports(request)).findFirst())
                .orElseThrow(() -> unsupported());
        return withMetadata(provider.parse(request), provider, parseMode, false, startedAt, warnings);
    }

    private Optional<DocumentParseProvider> findProvider(DocumentParseRequest request, DocumentParseProviderType providerType) {
        return providers.stream()
                .filter(provider -> provider.providerType() == providerType)
                .filter(provider -> provider.supports(request))
                .findFirst();
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
        return TranslationDocumentKnowledgePipeline.enrich(response);
    }

    private BizException unsupported() {
        return new BizException(ErrorCode.COMMON_VALIDATION_ERROR, "暂不支持该文件解析模式，请更换文件或解析方式");
    }
}
