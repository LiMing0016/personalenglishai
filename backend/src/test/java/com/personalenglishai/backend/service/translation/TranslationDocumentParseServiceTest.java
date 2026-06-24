package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
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
    void parsesPdfBookmarksIntoExplicitOutlineItems() throws Exception {
        byte[] pdf = bookmarkedPdf();

        TranslationDocumentParseResponse response = service.parsePdf("bookmarked.pdf", pdf);

        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOutline())
                .extracting("title")
                .containsExactly("Chapter 1 Algorithms", "1.1 Growth of Functions", "Chapter 2 Graphs");
        assertThat(response.getOutline())
                .extracting("level")
                .containsExactly(1, 2, 1);
        assertThat(response.getOutline())
                .extracting("pageNumber")
                .containsExactly(1, 1, 2);
        assertThat(response.getOutline())
                .extracting("source")
                .containsOnly("pdf_outline");
    }

    @Test
    void recognizesTableOfContentsPageWhenPdfHasNoBookmarks() throws Exception {
        byte[] pdf = tableOfContentsPdf();

        TranslationDocumentParseResponse response = service.parsePdf("toc.pdf", pdf);

        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOutline())
                .extracting("title")
                .containsExactly(
                        "Chapter 1 Foundations",
                        "1.1 Algorithms and Models",
                        "Chapter 2 Source Grounded Answers");
        assertThat(response.getOutline())
                .extracting("level")
                .containsExactly(1, 2, 1);
        assertThat(response.getOutline())
                .extracting("pageNumber")
                .containsExactly(3, 4, 5);
        assertThat(response.getOutline())
                .extracting("source")
                .containsOnly("toc_page");
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
    void preservesStructuredOcrElementsForKnowledgePipeline() {
        TranslationOcrElement element = new TranslationOcrElement(
                "paragraph",
                "OCR paragraph with precise location.",
                "[[10,20],[120,20],[120,60],[10,60]]",
                0.93,
                1,
                "paddle_ocr",
                "text",
                List.of("LOW_CONFIDENCE_TEXT")
        );
        TranslationDocumentParseService ocrService = new TranslationDocumentParseService(
                pdfBytes -> TranslationOcrResult.succeeded(List.of(
                        new TranslationOcrPageText(
                                1,
                                "OCR paragraph with precise location.",
                                List.of(element),
                                List.of("SPARSE_TEXT"),
                                800,
                                1200,
                                0.93,
                                "raw OCR paragraph",
                                "OCR paragraph with precise location."
                        )
                ), "{\"pages\":[{\"pageNumber\":1}]}")
        );

        TranslationDocumentParseResponse response = ocrService.parsePdf("scan.pdf", textPdf("   \n\n   "));

        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getProvider()).isEqualTo("paddle_ocr");
        assertThat(response.isFallbackUsed()).isTrue();
        assertThat(response.getElements()).hasSize(1);
        assertThat(response.getElements().get(0).getId()).isEqualTo("p1-ocr-e1");
        assertThat(response.getElements().get(0).getBbox()).contains("[[10,20]");
        assertThat(response.getElements().get(0).getMetadata())
                .containsEntry("source", "paddle_ocr")
                .containsEntry("rawType", "text");
        assertThat(response.getKnowledgeChunks().get(0).getSourceElementIds()).containsExactly("p1-ocr-e1");
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

    private static byte[] bookmarkedPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page1 = new PDPage();
            PDPage page2 = new PDPage();
            document.addPage(page1);
            document.addPage(page2);
            writePageText(document, page1, "Chapter 1 Algorithms",
                    "Algorithm analysis helps students connect definitions with examples and source evidence.");
            writePageText(document, page2, "Chapter 2 Graphs",
                    "Graph traversal material should stay tied to its page and source bookmark.");

            PDDocumentOutline outline = new PDDocumentOutline();
            document.getDocumentCatalog().setDocumentOutline(outline);
            PDOutlineItem chapter1 = outlineItem("Chapter 1 Algorithms", pageDestination(page1));
            PDOutlineItem section11 = outlineItem("1.1 Growth of Functions", pageDestination(page1));
            PDOutlineItem chapter2 = outlineItem("Chapter 2 Graphs", pageDestination(page2));
            chapter1.addLast(section11);
            outline.addLast(chapter1);
            outline.addLast(chapter2);
            chapter1.openNode();
            outline.openNode();

            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] tableOfContentsPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage contents = new PDPage();
            PDPage preface = new PDPage();
            PDPage chapter1 = new PDPage();
            PDPage section11 = new PDPage();
            PDPage chapter2 = new PDPage();
            document.addPage(contents);
            document.addPage(preface);
            document.addPage(chapter1);
            document.addPage(section11);
            document.addPage(chapter2);

            writePageLines(document, contents,
                    "Contents",
                    "Copyright and publisher information should not become a chapter",
                    "Chapter 1 Foundations ........ 3",
                    "1.1 Algorithms and Models .... 4",
                    "Chapter 2 Source Grounded",
                    "Answers ...................... 5");
            writePageText(document, preface, "Preface",
                    "This preface gives enough ordinary text for the parser to consider the PDF text layer usable.");
            writePageText(document, chapter1, "Chapter 1 Foundations",
                    "Foundational concepts explain why learning material must keep answers tied to source pages.");
            writePageText(document, section11, "1.1 Algorithms and Models",
                    "Algorithm sections need stable references so the agent can answer from selected source content.");
            writePageText(document, chapter2, "Chapter 2 Source Grounded Answers",
                    "Grounded answers should cite the page and section that supplied the explanation.");

            document.save(output);
            return output.toByteArray();
        }
    }

    private static void writePageText(PDDocument document, PDPage page, String heading, String body) throws Exception {
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, 14);
            content.newLineAtOffset(50, 740);
            content.showText(heading);
            content.newLineAtOffset(0, -24);
            content.setFont(PDType1Font.HELVETICA, 12);
            content.showText(body);
            content.endText();
        }
    }

    private static void writePageLines(PDDocument document, PDPage page, String... lines) throws Exception {
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 12);
            content.newLineAtOffset(50, 740);
            content.setLeading(18);
            for (String line : lines) {
                content.showText(line);
                content.newLine();
            }
            content.endText();
        }
    }

    private static PDOutlineItem outlineItem(String title, PDPageDestination destination) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(destination);
        return item;
    }

    private static PDPageDestination pageDestination(PDPage page) {
        PDPageXYZDestination destination = new PDPageXYZDestination();
        destination.setPage(page);
        return destination;
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
