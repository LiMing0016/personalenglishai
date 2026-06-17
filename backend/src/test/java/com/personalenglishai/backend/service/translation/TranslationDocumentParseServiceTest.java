package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationDocumentParseServiceTest {

    private final TranslationDocumentParseService service = new TranslationDocumentParseService(
            pdfBytes -> TranslationOcrResult.unavailable("OCR engine is not configured")
    );

    @Test
    void parsesTextPdfIntoOrderedLearningBlocks() {
        byte[] pdf = textPdf("""
                Agentic Translation Workspace

                AI is changing how students read foreign articles.
                It turns each paragraph into a focused learning unit.

                Students can collect phrases, grammar points, and review cards.
                """);

        TranslationDocumentParseResponse response = service.parsePdf("article.pdf", pdf);

        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOcrStatus()).isEqualTo("NOT_REQUIRED");
        assertThat(response.getPageCount()).isEqualTo(1);
        assertThat(response.getBlocks()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.getBlocks())
                .extracting("order")
                .containsExactlyElementsOf(response.getBlocks().stream().map(block -> block.getOrder()).toList());
        assertThat(response.getBlocks().get(0).getText()).contains("Agentic Translation Workspace");
        assertThat(response.getBlocks())
                .anySatisfy(block -> assertThat(block.getText()).contains("focused learning unit"));
    }

    @Test
    void enrichesParsedPdfWithDocumentElementsDiagnosisQualityAndChunks() {
        byte[] pdf = textPdf("""
                Chapter 1

                AI reading tools should keep every answer grounded in the source document.

                Good chunks preserve page numbers and source element ids.
                """);

        TranslationDocumentParseResponse response = service.parsePdf("article.pdf", pdf);

        assertThat(response.getElements()).hasSize(response.getBlocks().size());
        assertThat(response.getElements().get(0).getRecognitionStatus()).isEqualTo("READY");
        assertThat(response.getElements().get(0).getProvider()).isEqualTo("pdfbox");
        assertThat(response.getDiagnosis().getTextLayer()).isEqualTo("GOOD");
        assertThat(response.getDiagnosis().isOcrRecommended()).isFalse();
        assertThat(response.getQuality().getDocumentQualityScore()).isGreaterThan(0.7);
        assertThat(response.getKnowledgeChunks()).isNotEmpty();
        assertThat(response.getKnowledgeChunks().get(0).getSourceElementIds()).isNotEmpty();
        assertThat(response.getKnowledgeChunks().get(0).getPageNumbers()).contains(1);
        assertThat(response.getKnowledgeChunks().get(0).getSectionPath()).contains("Chapter 1");
        assertThat(response.getParseJob().getProvider()).isEqualTo("pdfbox");
        assertThat(response.getParseJob().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getLanguageProfile().getPrimaryLanguage()).isEqualTo("en");
    }

    @Test
    void marksBlankPdfAsRequiringOcr() {
        TranslationDocumentParseResponse response = service.parsePdf("scan.pdf", textPdf("   \n\n   "));

        assertThat(response.getParseStatus()).isEqualTo("NEEDS_OCR");
        assertThat(response.getOcrStatus()).isEqualTo("REQUIRED");
        assertThat(response.getBlocks()).isEmpty();
        assertThat(response.getWarnings()).anyMatch(warning -> warning.contains("OCR"));
        assertThat(response.getDiagnosis().getTextLayer()).isEqualTo("NONE");
        assertThat(response.getDiagnosis().isOcrRecommended()).isTrue();
        assertThat(response.getQuality().getDocumentQualityScore()).isLessThan(0.5);
        assertThat(response.getParseJob().getStatus()).isEqualTo("NEEDS_OCR");
        assertThat(response.getParseJob().getStage()).isEqualTo("PARSING");
        assertThat(response.getAssets()).anySatisfy(asset -> {
            assertThat(asset.getAssetType()).isEqualTo("page_snapshot");
            assertThat(asset.getRecognitionStatus()).isEqualTo("NEEDS_OCR");
            assertThat(asset.getPageNumber()).isEqualTo(1);
        });
    }

    @Test
    void usesOcrTextWhenPdfTextLayerIsBlank() {
        TranslationDocumentParseService ocrService = new TranslationDocumentParseService(
                pdfBytes -> TranslationOcrResult.succeeded(List.of(
                        new TranslationOcrPageText(1, """
                                Scanned article title

                                OCR recovered the paragraph from the scanned page.
                                """)
                ))
        );

        TranslationDocumentParseResponse response = ocrService.parsePdf("scan.pdf", textPdf("   \n\n   "));

        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOcrStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getBlocks()).isNotEmpty();
        assertThat(response.getBlocks()).anySatisfy(block ->
                assertThat(block.getText()).contains("OCR recovered the paragraph"));
    }

    @Test
    void usesOcrTextWhenPdfTextLayerLooksLikePromotionalNoise() {
        TranslationDocumentParseService ocrService = new TranslationDocumentParseService(
                pdfBytes -> TranslationOcrResult.succeeded(List.of(
                        new TranslationOcrPageText(1, """
                                The importance of environmental protection

                                People should protect forests and reduce pollution in daily life.
                                """)
                ))
        );

        byte[] pdf = textPdf("""
                Follow account for continuous updates. QQ: 378327010.
                environmental awareness protection ecosystem QQ: 378327010 en?ir?nment?friend
                QQ: 378327010 free materials and subscription updates.
                """);

        TranslationDocumentParseResponse response = ocrService.parsePdf("scan.pdf", pdf);

        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOcrStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getBlocks()).anySatisfy(block ->
                assertThat(block.getText()).contains("environmental protection"));
        assertThat(response.getBlocks()).noneSatisfy(block ->
                assertThat(block.getText()).contains("微信公众号"));
    }

    @Test
    void rejectsNonPdfFileName() {
        assertThatThrownBy(() -> service.parsePdf("article.txt", "hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("PDF");
    }

    private static byte[] textPdf(String text) {
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>");

        String stream = buildTextStream(text);
        objects.add("<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "\nendstream");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n")
                    .append(objects.get(i)).append("\n")
                    .append("endobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n")
                .append("0 ").append(objects.size() + 1).append("\n")
                .append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n")
                .append("<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n")
                .append("startxref\n")
                .append(xrefOffset).append("\n")
                .append("%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String buildTextStream(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder stream = new StringBuilder("BT\n/F1 12 Tf\n50 760 Td\n14 TL\n");
        for (String line : lines) {
            stream.append("(").append(escapePdfText(line)).append(") Tj\nT*\n");
        }
        stream.append("ET");
        return stream.toString();
    }

    private static String escapePdfText(String text) {
        return text.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
