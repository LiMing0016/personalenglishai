package com.personalenglishai.backend.service.translation;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.dto.translation.TranslationDocumentParseResponse;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationDocumentImportServiceTest {

    private final TranslationDocumentImportService service = new TranslationDocumentImportService(List.of(
            new PlainTextTranslationDocumentParser(),
            new MarkdownTranslationDocumentParser(),
            new DocxTranslationDocumentParser()
    ));

    @Test
    void importsTxtIntoOrderedParagraphBlocks() {
        TranslationDocumentParseResponse response = service.importDocument(new UploadedTranslationDocument(
                "reading.txt",
                "text/plain",
                "First paragraph.\n\nSecond paragraph for learning.".getBytes(StandardCharsets.UTF_8),
                "immersive"
        ));

        assertThat(response.getSourceType()).isEqualTo("TXT");
        assertThat(response.getParseStatus()).isEqualTo("SUCCEEDED");
        assertThat(response.getOcrStatus()).isEqualTo("NOT_REQUIRED");
        assertThat(response.getBlocks()).hasSize(2);
        assertThat(response.getBlocks().get(0).getType()).isEqualTo("paragraph");
        assertThat(response.getBlocks().get(1).getText()).contains("Second paragraph");
    }

    @Test
    void importsMarkdownHeadingsAndListsAsStructuredBlocks() {
        TranslationDocumentParseResponse response = service.importDocument(new UploadedTranslationDocument(
                "notes.md",
                "text/markdown",
                """
                        # Main Title

                        This is a normal paragraph.

                        - first expression
                        - second expression
                        """.getBytes(StandardCharsets.UTF_8),
                "immersive"
        ));

        assertThat(response.getSourceType()).isEqualTo("MD");
        assertThat(response.getBlocks()).extracting("type")
                .containsExactly("heading", "paragraph", "list", "list");
        assertThat(response.getBlocks().get(0).getText()).isEqualTo("Main Title");
    }

    @Test
    void importsDocxParagraphsAndHeadings() throws Exception {
        TranslationDocumentParseResponse response = service.importDocument(new UploadedTranslationDocument(
                "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes(),
                "immersive"
        ));

        assertThat(response.getSourceType()).isEqualTo("DOCX");
        assertThat(response.getBlocks()).hasSize(3);
        assertThat(response.getBlocks().get(0).getType()).isEqualTo("heading");
        assertThat(response.getBlocks().get(1).getText()).contains("Word documents should become readable blocks");
        assertThat(response.getBlocks().get(2).getType()).isEqualTo("table");
        assertThat(response.getBlocks().get(2).getText()).contains("Phrase", "Meaning");
    }

    @Test
    void rejectsLegacyDocWithClearMessage() {
        assertThatThrownBy(() -> service.importDocument(new UploadedTranslationDocument(
                "legacy.doc",
                "application/msword",
                "not a docx".getBytes(StandardCharsets.UTF_8),
                "immersive"
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("DOCX");
    }

    private static byte[] docxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Document Title");

            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("Word documents should become readable blocks.");

            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Phrase");
            table.getRow(0).getCell(1).setText("Meaning");
            table.getRow(1).getCell(0).setText("break down");
            table.getRow(1).getCell(1).setText("拆解");

            document.write(output);
            return output.toByteArray();
        }
    }
}
