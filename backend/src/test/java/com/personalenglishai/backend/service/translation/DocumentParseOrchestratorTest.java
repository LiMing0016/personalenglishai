package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParseOrchestratorTest {

    @Test
    void standardModeUsesPaddleProviderBeforeLocalProviderAndAddsParseMetadata() {
        FakeProvider baiduProvider = FakeProvider.success(DocumentParseProviderType.BAIDU_PADDLE_VL);
        FakeProvider paddleProvider = FakeProvider.success(DocumentParseProviderType.PADDLE_OCR);
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(localProvider, paddleProvider, baiduProvider), true, false);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "article.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.STANDARD,
                "immersive"
        ));

        assertThat(baiduProvider.callCount).isZero();
        assertThat(paddleProvider.callCount).isEqualTo(1);
        assertThat(localProvider.callCount).isZero();
        assertThat(response.getProvider()).isEqualTo("paddle-ocr");
        assertThat(response.getParseMode()).isEqualTo("standard");
        assertThat(response.isFallbackUsed()).isFalse();
        assertThat(response.getElapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBlocks()).extracting("text").containsExactly("parsed by paddle-ocr");
    }

    @Test
    void highQualityModeUsesLocalPaddleVlProviderBeforeCloudPaddleVlPaddleThirdPartyAndLocalProvider() {
        FakeProvider localPaddleVlProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PADDLE_VL);
        FakeProvider baiduProvider = FakeProvider.success(DocumentParseProviderType.BAIDU_PADDLE_VL);
        FakeProvider paddleProvider = FakeProvider.success(DocumentParseProviderType.PADDLE_OCR);
        FakeProvider thirdPartyProvider = FakeProvider.success(DocumentParseProviderType.THIRD_PARTY_LAYOUT);
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(
                thirdPartyProvider,
                localProvider,
                paddleProvider,
                baiduProvider,
                localPaddleVlProvider
        ), true, false);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive"
        ));

        assertThat(localPaddleVlProvider.callCount).isEqualTo(1);
        assertThat(baiduProvider.callCount).isZero();
        assertThat(paddleProvider.callCount).isZero();
        assertThat(thirdPartyProvider.callCount).isZero();
        assertThat(localProvider.callCount).isZero();
        assertThat(response.getProvider()).isEqualTo("local-paddle-vl");
        assertThat(response.getParseMode()).isEqualTo("high_quality");
        assertThat(response.isFallbackUsed()).isFalse();
    }

    @Test
    void highQualityModeUsesPaddleProviderWhenPaddleProviderIsRequested() {
        FakeProvider localPaddleVlProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PADDLE_VL);
        FakeProvider paddleProvider = FakeProvider.success(DocumentParseProviderType.PADDLE_OCR);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(localPaddleVlProvider, paddleProvider), true, false);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive",
                DocumentParseProviderPreference.PADDLE_OCR
        ));

        assertThat(localPaddleVlProvider.callCount).isZero();
        assertThat(paddleProvider.callCount).isEqualTo(1);
        assertThat(response.getProvider()).isEqualTo("paddle-ocr");
        assertThat(response.isFallbackUsed()).isFalse();
    }

    @Test
    void highQualityModeDoesNotUseCloudWhenLocalPaddleVlIsRequestedAndFails() {
        FakeProvider localPaddleVlProvider = FakeProvider.failure(DocumentParseProviderType.LOCAL_PADDLE_VL);
        FakeProvider baiduProvider = FakeProvider.success(DocumentParseProviderType.BAIDU_PADDLE_VL);
        FakeProvider paddleProvider = FakeProvider.success(DocumentParseProviderType.PADDLE_OCR);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(paddleProvider, baiduProvider, localPaddleVlProvider), true, false);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive",
                DocumentParseProviderPreference.LOCAL_PADDLE_VL
        ));

        assertThat(localPaddleVlProvider.callCount).isEqualTo(1);
        assertThat(baiduProvider.callCount).isZero();
        assertThat(paddleProvider.callCount).isEqualTo(1);
        assertThat(response.getProvider()).isEqualTo("paddle-ocr");
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("本地 PaddleOCR-VL 解析失败"));
    }

    @Test
    void highQualityModeFallsBackToLocalPaddleWhenLocalPaddleVlFailsAndCloudIsDisabled() {
        FakeProvider localPaddleVlProvider = FakeProvider.failure(DocumentParseProviderType.LOCAL_PADDLE_VL);
        FakeProvider paddleProvider = FakeProvider.success(DocumentParseProviderType.PADDLE_OCR);
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(localProvider, paddleProvider, localPaddleVlProvider), true, false);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive"
        ));

        assertThat(localPaddleVlProvider.callCount).isEqualTo(1);
        assertThat(paddleProvider.callCount).isEqualTo(1);
        assertThat(localProvider.callCount).isZero();
        assertThat(response.getProvider()).isEqualTo("paddle-ocr");
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("本地 PaddleOCR-VL 解析失败"));
    }

    @Test
    void highQualityModeCanStillUseBaiduPaddleVlWhenLocalPaddleVlIsUnavailableAndCloudIsEnabled() {
        FakeProvider localPaddleVlProvider = FakeProvider.failure(DocumentParseProviderType.LOCAL_PADDLE_VL);
        FakeProvider baiduProvider = FakeProvider.success(DocumentParseProviderType.BAIDU_PADDLE_VL);
        FakeProvider paddleProvider = FakeProvider.success(DocumentParseProviderType.PADDLE_OCR);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(paddleProvider, baiduProvider, localPaddleVlProvider), true, false);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive"
        ));

        assertThat(localPaddleVlProvider.callCount).isEqualTo(1);
        assertThat(baiduProvider.callCount).isEqualTo(1);
        assertThat(paddleProvider.callCount).isZero();
        assertThat(response.getProvider()).isEqualTo("baidu-paddle-vl");
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("本地 PaddleOCR-VL 解析失败"));
    }

    @Test
    void paddleFailureDoesNotFallbackToLocalProviderWhenPdfBoxFallbackIsDisabled() {
        FakeProvider paddleProvider = FakeProvider.failure(DocumentParseProviderType.PADDLE_OCR);
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(paddleProvider, localProvider), true, false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.STANDARD,
                "immersive"
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(paddleProvider.callCount).isEqualTo(1);
        assertThat(localProvider.callCount).isZero();
    }

    @Test
    void paddleFailureFallsBackToLocalProviderWhenPdfBoxFallbackIsEnabled() {
        FakeProvider paddleProvider = FakeProvider.failure(DocumentParseProviderType.PADDLE_OCR);
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(paddleProvider, localProvider), true, true);

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.STANDARD,
                "immersive"
        ));

        assertThat(paddleProvider.callCount).isEqualTo(1);
        assertThat(localProvider.callCount).isEqualTo(1);
        assertThat(response.getProvider()).isEqualTo("local-pdfbox");
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("PaddleOCR 解析失败"));
    }

    private static final class FakeProvider implements DocumentParseProvider {
        private final DocumentParseProviderType providerType;
        private final boolean fail;
        private int callCount;

        private FakeProvider(DocumentParseProviderType providerType, boolean fail) {
            this.providerType = providerType;
            this.fail = fail;
        }

        static FakeProvider success(DocumentParseProviderType providerType) {
            return new FakeProvider(providerType, false);
        }

        static FakeProvider failure(DocumentParseProviderType providerType) {
            return new FakeProvider(providerType, true);
        }

        @Override
        public boolean supports(DocumentParseRequest request) {
            return "PDF".equalsIgnoreCase(request.fileType());
        }

        @Override
        public DocumentParseProviderType providerType() {
            return providerType;
        }

        @Override
        public TranslationDocumentParseResponse parse(DocumentParseRequest request) {
            callCount++;
            if (fail) {
                throw new IllegalStateException("provider unavailable");
            }
            TranslationDocumentParseResponse response = new TranslationDocumentParseResponse();
            response.setFileName(request.originalFilename());
            response.setSourceType("PDF");
            response.setParseStatus("SUCCEEDED");
            response.setOcrStatus("NOT_REQUIRED");
            response.setPageCount(1);
            response.setBlocks(List.of(new TranslationDocumentBlockDto(
                    providerType.wireName() + "-b1",
                    "paragraph",
                    1,
                    1,
                    "parsed by " + providerType.wireName(),
                    null
            )));
            return response;
        }
    }
}
