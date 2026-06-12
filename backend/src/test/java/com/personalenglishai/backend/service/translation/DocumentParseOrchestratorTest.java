package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.dto.translation.TranslationDocumentBlockDto;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParseOrchestratorTest {

    @Test
    void standardModeUsesLocalProviderAndAddsParseMetadata() {
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(localProvider));

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "article.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.STANDARD,
                "immersive"
        ));

        assertThat(localProvider.callCount).isEqualTo(1);
        assertThat(response.getProvider()).isEqualTo("local-pdfbox");
        assertThat(response.getParseMode()).isEqualTo("standard");
        assertThat(response.isFallbackUsed()).isFalse();
        assertThat(response.getElapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.getBlocks()).extracting("text").containsExactly("parsed by local-pdfbox");
    }

    @Test
    void highQualityModeFallsBackToLocalProviderWhenThirdPartyFails() {
        FakeProvider thirdPartyProvider = FakeProvider.failure(DocumentParseProviderType.THIRD_PARTY_LAYOUT);
        FakeProvider localProvider = FakeProvider.success(DocumentParseProviderType.LOCAL_PDFBOX);
        DocumentParseOrchestrator orchestrator = new DocumentParseOrchestrator(List.of(thirdPartyProvider, localProvider));

        TranslationDocumentParseResponse response = orchestrator.parse(new DocumentParseRequest(
                "scan.pdf",
                "application/pdf",
                "pdf bytes".getBytes(StandardCharsets.UTF_8),
                "PDF",
                DocumentParseMode.HIGH_QUALITY,
                "immersive"
        ));

        assertThat(thirdPartyProvider.callCount).isEqualTo(1);
        assertThat(localProvider.callCount).isEqualTo(1);
        assertThat(response.getProvider()).isEqualTo("local-pdfbox");
        assertThat(response.getParseMode()).isEqualTo("high_quality");
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("高质量解析失败"));
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
